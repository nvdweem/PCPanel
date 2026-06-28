package com.getpcpanel.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

import javax.annotation.Nullable;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.analogbands.command.AnalogBand;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.analogbands.command.CommandAnalogBands;
import com.getpcpanel.device.Device;
import com.getpcpanel.hid.BrightnessService;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.profile.BaseLayerService;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.dto.LightingConfig;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig.SINGLE_KNOB_MODE;
import com.getpcpanel.profile.dto.SingleSliderLightingConfig;
import com.getpcpanel.profile.dto.SingleSliderLightingConfig.SINGLE_SLIDER_MODE;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import lombok.extern.log4j.Log4j2;

/**
 * Surfaces the <em>resolved</em> per-control state of the base-layer + stepped-switch features, which is
 * otherwise invisible to the harness: which profile a control's command/lighting actually came from
 * (active vs base layer), and a stepped switch's live selected band + feedback colour. It computes
 * directly from persistence via {@link BaseLayerService}, so it works for offline persisted devices too
 * (the PCPanel lighting/visual path needs real hardware, but this resolution is pure logic).
 *
 * <p>Typical loop: configure a control via the REST API, drive it with {@code pcpanel_simulate_analog} /
 * {@code pcpanel_simulate_button}, then read this tool to assert the band advanced / the fallback fired /
 * the colour resolved.
 */
@Log4j2
@ApplicationScoped
@IfBuildProperty(name = McpDevTool.FLAG, stringValue = "true")
public class DebugResolveTools {
    @Inject DeviceHolder deviceHolder;
    @Inject SaveService saveService;
    @Inject BaseLayerService baseLayer;
    @Inject BrightnessService brightnessService;

    @Tool(description = "Resolve a device's controls through the base-layer + stepped-switch logic: per "
            + "control, which profile its command and lighting came from (active|baseLayer|none), and for "
            + "a stepped switch (CommandAnalogBands) its live selected band index + feedback colour. Works "
            + "for offline persisted devices. Use it to assert base-layer fallback and stepped-switch "
            + "transitions after pcpanel_simulate_analog / _button. Returns found=false for an unknown serial.")
    public DebugResolve pcpanel_debug_resolve(@ToolArg(description = "Device serial / id") String serial) {
        var deviceSave = saveService.get() == null ? null : saveService.get().getDevices().get(serial);
        if (deviceSave == null) {
            return new DebugResolve(false, "No device (live or persisted) with serial '" + serial + "'", serial, null, null, null, List.of());
        }
        var active = activeProfile(serial, deviceSave);
        if (active == null) {
            return new DebugResolve(false, "Device '" + serial + "' has no profiles", serial, null, null, null, List.of());
        }
        // Runtime global brightness driven by a brightness dial (any profile), or null when none is configured.
        var rb = brightnessService.runtimeBrightness(serial);
        Integer runtimeBrightness = rb.isPresent() ? rb.getAsInt() : null;
        var base = baseLayer.baseLayer(serial).filter(b -> b != active).orElse(null);
        var effectiveLc = baseLayer.effectiveLighting(serial, active.lightingConfig());
        var knobLen = effectiveLc == null ? 0 : effectiveLc.knobConfigs().length;

        var controls = new ArrayList<ControlResolution>();
        for (var idx : sortedKeys(active.getDialData(), base == null ? null : base.getDialData())) {
            var cmd = commandResolution(active.getDialData().get(idx), base == null ? null : base.getDialData().get(idx));
            var stepped = steppedSwitch(active, base, idx);
            var light = lightResolution(active.lightingConfig(), effectiveLc, idx, knobLen);
            var kind = idx < knobLen ? "knob" : "slider";
            controls.add(new ControlResolution(idx, kind, cmd, stepped, light));
        }
        for (var idx : sortedKeys(active.getButtonData(), base == null ? null : base.getButtonData())) {
            var cmd = commandResolution(nonEmpty(active.getButtonData(idx)), base == null ? null : nonEmpty(base.getButtonData(idx)));
            controls.add(new ControlResolution(idx, "button", cmd, null, null));
        }
        return new DebugResolve(true, null, serial, active.getName(), base == null ? null : base.getName(), runtimeBrightness, controls);
    }

    private Profile activeProfile(String serial, DeviceSave deviceSave) {
        var live = deviceHolder.getDevice(serial).map(Device::currentProfile).orElse(null);
        if (live != null) {
            return live;
        }
        var byName = deviceSave.getProfile(deviceSave.getCurrentProfileName());
        return byName.orElseGet(() -> deviceSave.getProfiles().isEmpty() ? null : deviceSave.getProfiles().get(0));
    }

    /** Resolves a control's effective command and where it came from (active wins, then base layer). */
    private static CommandResolution commandResolution(@Nullable Commands own, @Nullable Commands base) {
        if (Commands.hasCommands(own)) {
            return describe("active", own);
        }
        if (Commands.hasCommands(base)) {
            return describe("baseLayer", base);
        }
        return new CommandResolution("none", null, null);
    }

    private static CommandResolution describe(String source, Commands cmds) {
        var first = cmds.getCommands().get(0);
        return new CommandResolution(source, first.getClass().getName(), first.buildLabel());
    }

    /** Live state of a stepped switch at this control (from whichever profile owns it), or null. */
    private static SteppedSwitch steppedSwitch(Profile active, @Nullable Profile base, int idx) {
        var cmd = stepped(active.getDialData().get(idx))
                .or(() -> base == null ? Optional.empty() : stepped(base.getDialData().get(idx)))
                .orElse(null);
        if (cmd == null) {
            return null;
        }
        var bands = new ArrayList<BandInfo>();
        for (var b : cmd.getBands()) {
            bands.add(new BandInfo(b.start(), b.end(), b.color(), actionType(b)));
        }
        return new SteppedSwitch(cmd.getBands().size(), cmd.getCurrentBand(), cmd.getCurrentColor(), bands);
    }

    private static Optional<CommandAnalogBands> stepped(@Nullable Commands cmds) {
        return cmds == null ? Optional.empty() : cmds.getCommand(CommandAnalogBands.class);
    }

    private static @Nullable String actionType(AnalogBand b) {
        if (!Commands.hasCommands(b.commands())) {
            return null;
        }
        return b.commands().getCommands().get(0).getClass().getName();
    }

    /** Resolves a control's effective LED config + where it came from (active per-control, then base layer). */
    private static LightResolution lightResolution(@Nullable LightingConfig activeLc, @Nullable LightingConfig effectiveLc, int idx, int knobLen) {
        if (effectiveLc == null) {
            return new LightResolution("none", null, null);
        }
        if (idx < knobLen) {
            var eff = effectiveLc.knobConfigs()[idx];
            var act = activeLc != null && idx < activeLc.knobConfigs().length ? activeLc.knobConfigs()[idx] : null;
            var source = act != null && act.getMode() != SINGLE_KNOB_MODE.NONE ? "active"
                    : eff != null && eff.getMode() != SINGLE_KNOB_MODE.NONE ? "baseLayer" : "none";
            return new LightResolution(source, eff == null ? null : String.valueOf(eff.getMode()), knobColor(eff));
        }
        var slider = idx - knobLen;
        if (effectiveLc.sliderConfigs().length <= slider) {
            return new LightResolution("none", null, null);
        }
        var eff = effectiveLc.sliderConfigs()[slider];
        var act = activeLc != null && slider < activeLc.sliderConfigs().length ? activeLc.sliderConfigs()[slider] : null;
        var source = act != null && act.getMode() != SINGLE_SLIDER_MODE.NONE ? "active"
                : eff != null && eff.getMode() != SINGLE_SLIDER_MODE.NONE ? "baseLayer" : "none";
        return new LightResolution(source, eff == null ? null : String.valueOf(eff.getMode()), sliderColor(eff));
    }

    private static @Nullable String knobColor(@Nullable SingleKnobLightingConfig c) {
        return c == null || c.getMode() == SINGLE_KNOB_MODE.NONE ? null : c.getColor1();
    }

    private static @Nullable String sliderColor(@Nullable SingleSliderLightingConfig c) {
        return c == null || c.getMode() == SINGLE_SLIDER_MODE.NONE ? null : c.getColor1();
    }

    private static @Nullable Commands nonEmpty(@Nullable Commands c) {
        return Commands.hasCommands(c) ? c : null;
    }

    private static Iterable<Integer> sortedKeys(java.util.Map<Integer, Commands> a, @Nullable java.util.Map<Integer, Commands> b) {
        var keys = new TreeSet<Integer>();
        if (a != null) {
            keys.addAll(a.keySet());
        }
        if (b != null) {
            keys.addAll(b.keySet());
        }
        return keys;
    }

    public record DebugResolve(boolean found, String error, String serial, String activeProfile, String baseLayer,
                               Integer runtimeBrightness, List<ControlResolution> controls) {
    }

    public record ControlResolution(int index, String kind, CommandResolution command, @Nullable SteppedSwitch steppedSwitch, @Nullable LightResolution light) {
    }

    public record CommandResolution(String source, String type, String label) {
    }

    public record SteppedSwitch(int bandCount, int currentBand, String currentColor, List<BandInfo> bands) {
    }

    public record BandInfo(double start, double end, String color, String actionType) {
    }

    public record LightResolution(String source, String mode, String color) {
    }
}
