package com.getpcpanel.mutecolor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.cpp.AudioDeviceEvent;
import com.getpcpanel.cpp.AudioSessionEvent;
import com.getpcpanel.device.Device;
import com.getpcpanel.discord.DiscordChangedEvent;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.obs.OBSConnectEvent;
import com.getpcpanel.obs.OBSMuteEvent;
import com.getpcpanel.profile.BaseLayerService;
import com.getpcpanel.profile.LightingChangedToDefaultEvent;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.profile.ProfileSwitchedEvent;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.dto.LightingConfig;
import com.getpcpanel.profile.dto.LightingConfig.LightingMode;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig.SINGLE_KNOB_MODE;
import com.getpcpanel.profile.dto.SingleSliderLabelLightingConfig;
import com.getpcpanel.profile.dto.SingleSliderLabelLightingConfig.SINGLE_SLIDER_LABEL_MODE;
import com.getpcpanel.profile.dto.SingleSliderLightingConfig;
import com.getpcpanel.profile.dto.SingleSliderLightingConfig.SINGLE_SLIDER_MODE;
import com.getpcpanel.rest.EventBroadcaster.LightingChangedEvent;
import com.getpcpanel.rest.EventBroadcaster.VisualColorsChangedEvent;
import com.getpcpanel.util.coloroverride.ColorOverrideHolder;
import com.getpcpanel.util.coloroverride.IOverrideColorProvider;
import com.getpcpanel.util.coloroverride.IOverrideColorProviderProvider;
import com.getpcpanel.wavelink.WaveLinkChangedEvent;

import io.quarkus.arc.All;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * Drives the per-control "mute override" colour: when the audio target a dial/slider/label controls is
 * muted, its LED switches to the configured muted colour, and reverts when unmuted. Works for anything
 * mutable, via a registry of {@link MuteStateResolver}s (process, audio device, Wave Link, OBS,
 * VoiceMeeter, …).
 *
 * <p>On any mute-relevant event the whole set of override-configured controls is recomputed by pulling
 * the current mute state from the resolvers — a simpler and more robust model than matching each event
 * to specific controls. Overrides are stored in a {@link ColorOverrideHolder} consulted by the lighting
 * output path ({@code OutputInterpreter}) and the UI colour service ({@code ProVisualColorsService});
 * after applying them this service both re-sends lighting to the hardware and fires
 * {@link VisualColorsChangedEvent} so the on-screen device re-renders.
 *
 * <p>The override is active whenever a control has a non-black muted colour. The target it follows is
 * the control's {@code muteOverrideDeviceOrFollow}: blank means {@link MuteStateResolver#FOLLOW} (follow
 * the control's own command), a non-blank value names a fixed device / VoiceMeeter target.
 */
@Log4j2
@Priority(0)
@ApplicationScoped
public class MuteColorService implements IOverrideColorProviderProvider {
    @Inject
    DeviceHolder devices;
    @Inject
    SaveService saveService;
    @Inject
    BaseLayerService baseLayer;
    @Inject
    @All
    List<MuteStateResolver> resolvers;
    @Inject
    Event<VisualColorsChangedEvent> visualColorsChanged;

    private final ColorOverrideHolder holder = new ColorOverrideHolder();

    // ── recompute triggers ──────────────────────────────────────────────────────
    public void onDeviceConnected(@Observes DeviceHolder.DeviceFullyConnectedEvent event) {
        recomputeAll();
    }

    public void onLightingChangedToDefault(@Observes LightingChangedToDefaultEvent event) {
        recomputeAll();
    }

    public void onProfileSwitched(@Observes ProfileSwitchedEvent event) {
        recomputeAll();
    }

    public void onLightingChanged(@Observes LightingChangedEvent event) {
        // The user edited lighting (incl. the muted colour or the follow target) — re-evaluate so a
        // changed muted colour previews immediately even while the target is already muted.
        recomputeAll();
    }

    public void onAudioSession(@Observes AudioSessionEvent event) {
        recomputeAll();
    }

    public void onAudioDevice(@Observes AudioDeviceEvent event) {
        recomputeAll();
    }

    public void onObsMute(@Observes OBSMuteEvent event) {
        recomputeAll();
    }

    public void onObsConnect(@Observes OBSConnectEvent event) {
        recomputeAll();
    }

    public void onWaveLink(@Observes WaveLinkChangedEvent event) {
        recomputeAll();
    }

    public void onDiscord(@Observes DiscordChangedEvent event) {
        recomputeAll();
    }

    public void onDirty(@Observes MuteOverridesDirtyEvent event) {
        recomputeAll();
    }

    public synchronized void recomputeAll() {
        log.debug("Mute-colour recompute triggered");
        for (var serial : saveService.get().getDevices().keySet()) {
            devices.getDevice(serial).ifPresent(device -> recomputeDevice(serial, device));
        }
    }

    private void recomputeDevice(String serial, Device device) {
        LightingConfig lc;
        Profile profile;
        try {
            lc = device.lightingConfig();
            profile = device.currentProfile();
        } catch (Exception e) {
            log.debug("Skipping mute-override recompute for {}", serial, e);
            return;
        }
        // Overrides are only consulted in CUSTOM mode; other modes show global lighting. Leaving stale
        // overrides in the holder is harmless (never read) and they are corrected on the next recompute
        // that a profile/lighting switch triggers.
        if (lc == null || lc.lightingMode() != LightingMode.CUSTOM) {
            log.debug("Device {} not in CUSTOM lighting (mode={}); no mute overrides applied", serial, lc == null ? null : lc.lightingMode());
            return;
        }
        // Resolve over the base-layer-merged view so a control configured only in the base layer (its
        // command and its muted colour) still gets a mute override. The re-send below merges on output.
        var effectiveLc = baseLayer.effectiveLighting(serial, lc);
        var commandsByControl = controlCommandsForMute(serial, profile);
        if (applyOverrides(serial, effectiveLc, commandsByControl)) {
            log.debug("Mute overrides changed for {}; re-rendering", serial);
            try {
                device.setLighting(lc, true);
            } catch (Exception e) {
                log.error("Unable to re-send mute-override lighting for {}", serial, e);
            }
            visualColorsChanged.fire(new VisualColorsChangedEvent(serial));
        }
    }

    /**
     * Per-control command list the mute-override colour follows: the control's volume dial merged with
     * its mute button (same control index), so a control configured with an OBS/Wave Link mute
     * <em>button</em> drives its LED just like a volume dial does. Base-layer fallbacks are honoured for
     * each half (dial via {@code effectiveDialData}, button via {@code effectiveButton}).
     */
    private Map<Integer, Commands> controlCommandsForMute(String serial, Profile profile) {
        var merged = new HashMap<>(baseLayer.effectiveDialData(serial, profile));
        for (var idx : profile.getButtonData().keySet()) {
            var button = baseLayer.effectiveButton(serial, profile, idx);
            if (Commands.hasCommands(button)) {
                merged.merge(idx, button, MuteColorService::concat);
            }
        }
        return merged;
    }

    private static Commands concat(Commands dial, Commands button) {
        var all = new ArrayList<Command>(dial.getCommands());
        all.addAll(button.getCommands());
        return new Commands(all, dial.getType());
    }

    /** Applies/clears overrides for one device's controls from its lighting config + per-control
     *  (dial + mute-button) assignments; returns whether any control's override colour changed.
     *  Package-visible for unit testing. */
    boolean applyOverrides(String serial, LightingConfig lc, Map<Integer, Commands> dialData) {
        var knobConfigs = lc.knobConfigs();
        var sliderConfigs = lc.sliderConfigs();
        var labelConfigs = lc.sliderLabelConfigs();
        var knobLen = knobConfigs.length;
        var changed = false;

        for (var entry : dialData.entrySet()) {
            var idx = entry.getKey();
            var command = entry.getValue();
            if (idx < knobLen) {
                changed |= applyDial(serial, idx, knobConfigs[idx], command);
            } else {
                var slider = idx - knobLen;
                if (slider >= 0 && slider < sliderConfigs.length) {
                    changed |= applySlider(serial, slider, sliderConfigs[slider], command);
                }
                if (slider >= 0 && slider < labelConfigs.length) {
                    changed |= applyLabel(serial, slider, labelConfigs[slider], command);
                }
            }
        }
        return changed;
    }

    private boolean applyDial(String serial, int idx, SingleKnobLightingConfig cfg, Commands command) {
        var color = cfg.getMuteOverrideColor();
        var target = cfg.getMuteOverrideDeviceOrFollow();
        var wanted = !isOff(color) && resolveMuted(command, target)
                ? new SingleKnobLightingConfig().setMode(SINGLE_KNOB_MODE.STATIC)
                                                .setColor1(color)
                                                .setMuteOverrideColor(color)
                                                .setMuteOverrideDeviceOrFollow(target)
                : null;
        var current = holder.getDialOverride(serial, idx).orElse(null);
        if (sameColor(current == null ? null : current.getColor1(), wanted == null ? null : wanted.getColor1())) {
            return false;
        }
        holder.setDialOverride(serial, idx, wanted);
        return true;
    }

    private boolean applySlider(String serial, int idx, SingleSliderLightingConfig cfg, Commands command) {
        var color = cfg.getMuteOverrideColor();
        var target = cfg.getMuteOverrideDeviceOrFollow();
        var wanted = !isOff(color) && resolveMuted(command, target)
                ? new SingleSliderLightingConfig().setMode(SINGLE_SLIDER_MODE.STATIC)
                                                  .setColor1(color)
                                                  .setMuteOverrideColor(color)
                                                  .setMuteOverrideDeviceOrFollow(target)
                : null;
        var current = holder.getSliderOverride(serial, idx).orElse(null);
        if (sameColor(current == null ? null : current.getColor1(), wanted == null ? null : wanted.getColor1())) {
            return false;
        }
        holder.setSliderOverride(serial, idx, wanted);
        return true;
    }

    private boolean applyLabel(String serial, int idx, SingleSliderLabelLightingConfig cfg, Commands command) {
        var color = cfg.getMuteOverrideColor();
        var target = cfg.getMuteOverrideDeviceOrFollow();
        var wanted = !isOff(color) && resolveMuted(command, target)
                ? new SingleSliderLabelLightingConfig().setMode(SINGLE_SLIDER_LABEL_MODE.STATIC)
                                                       .setColor(color)
                                                       .setMuteOverrideColor(color)
                                                       .setMuteOverrideDeviceOrFollow(target)
                : null;
        var current = holder.getSliderLabelOverride(serial, idx).orElse(null);
        if (sameColor(current == null ? null : current.getColor(), wanted == null ? null : wanted.getColor())) {
            return false;
        }
        holder.setSliderLabelOverride(serial, idx, wanted);
        return true;
    }

    private static boolean sameColor(String a, String b) {
        return Objects.equals(a, b);
    }

    /** First resolver that claims responsibility wins; an unclaimed target counts as not muted. */
    private boolean resolveMuted(Commands command, String target) {
        var normalized = StringUtils.isBlank(target) ? MuteStateResolver.FOLLOW : target;
        for (var resolver : resolvers) {
            try {
                var result = resolver.resolve(command, normalized);
                if (result.isPresent()) {
                    log.debug("Mute target '{}' resolved by {} -> {}", normalized, resolver.getClass().getSimpleName(), result.get());
                    return result.get();
                }
            } catch (RuntimeException e) {
                // A single misbehaving resolver must not abort recomputeAll() for every device, nor
                // propagate onto the (often native audio / websocket) thread that fired the event.
                log.warn("Mute resolver {} failed for target '{}'; treating as not muted", resolver.getClass().getSimpleName(), normalized, e);
            }
        }
        var first = command.getCommands().isEmpty() ? "none" : command.getCommands().get(0).getClass().getSimpleName();
        log.debug("No resolver claimed mute target '{}' (command={}); treating as not muted", normalized, first);
        return false;
    }

    /** Only a blank/absent muted colour means "no mute override configured". Off is its own state (the
     *  UI toggle clears the colour to blank), so an explicit colour — including black — is honoured. */
    private static boolean isOff(String color) {
        return StringUtils.isBlank(color);
    }

    @Override
    public IOverrideColorProvider getOverrideColorProvider() {
        return holder;
    }
}
