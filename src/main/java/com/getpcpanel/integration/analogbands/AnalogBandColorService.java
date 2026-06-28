package com.getpcpanel.integration.analogbands;

import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.integration.analogbands.command.CommandAnalogBands;
import com.getpcpanel.device.Device;
import com.getpcpanel.device.DeviceHolder;
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

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * Drives the per-position LED feedback colour of a {@link CommandAnalogBands} stepped switch: whichever
 * band the dial/slider currently rests on lights its control in that band's configured colour. Like the
 * mute-colour overrides this only applies in {@code CUSTOM} lighting mode (other modes show global
 * lighting), and works by storing a {@link ColorOverrideHolder} override consulted by the lighting
 * output path ({@code OutputInterpreter}) and the UI colour service ({@code ProVisualColorsService}).
 *
 * <p>It is registered with a lower priority than {@code MuteColorService}, so when a control is both a
 * stepped switch and a muted audio target the mute colour wins.
 *
 * <p>Recompute is triggered by {@link CommandAnalogBands} itself whenever its selected position changes
 * (via {@link #refresh(String)}), and on profile switch / connect / lighting edits so the position's
 * colour is shown immediately.
 */
@Log4j2
@Priority(-100)
@ApplicationScoped
public class AnalogBandColorService implements IOverrideColorProviderProvider {
    @Inject
    DeviceHolder devices;
    @Inject
    SaveService saveService;
    @Inject
    BaseLayerService baseLayer;
    @Inject
    Event<VisualColorsChangedEvent> visualColorsChanged;

    private final ColorOverrideHolder holder = new ColorOverrideHolder();

    public void onDeviceConnected(@Observes DeviceHolder.DeviceFullyConnectedEvent event) {
        refreshAll();
    }

    public void onProfileSwitched(@Observes ProfileSwitchedEvent event) {
        refresh(event.serial());
    }

    public void onLightingChangedToDefault(@Observes LightingChangedToDefaultEvent event) {
        refresh(event.serialNum());
    }

    public void onLightingChanged(@Observes LightingChangedEvent event) {
        // The user may have edited a band's feedback colour; re-evaluate so it previews immediately.
        refresh(event.serial());
    }

    public synchronized void refreshAll() {
        for (var serial : saveService.get().getDevices().keySet()) {
            refresh(serial);
        }
    }

    /** Recomputes one device's per-position overrides and re-renders if anything changed. */
    public synchronized void refresh(String serial) {
        devices.getDevice(serial).ifPresent(device -> {
            LightingConfig lc;
            Profile profile;
            try {
                lc = device.lightingConfig();
                profile = device.currentProfile();
            } catch (Exception e) {
                log.debug("Skipping analog-band colour refresh for {}", serial, e);
                return;
            }
            if (lc == null || lc.lightingMode() != LightingMode.CUSTOM) {
                return;
            }
            // Include base-layer controls so a stepped switch defined only in the base layer also lights.
            var effectiveLc = baseLayer.effectiveLighting(serial, lc);
            var effectiveDialData = baseLayer.effectiveDialData(serial, profile);
            if (applyOverrides(serial, effectiveLc, effectiveDialData)) {
                try {
                    device.setLighting(lc, true);
                } catch (Exception e) {
                    log.error("Unable to re-send analog-band lighting for {}", serial, e);
                }
                visualColorsChanged.fire(new VisualColorsChangedEvent(serial));
            }
        });
    }

    /** Applies/clears the selected-position colour for every control holding a stepped switch; returns
     *  whether any override changed. Package-visible for testing. */
    boolean applyOverrides(String serial, LightingConfig lc, Map<Integer, Commands> dialData) {
        var knobLen = lc.knobConfigs().length;
        var sliderLen = lc.sliderConfigs().length;
        var labelLen = lc.sliderLabelConfigs().length;
        var changed = false;

        for (var entry : dialData.entrySet()) {
            var idx = entry.getKey();
            var color = colorFor(entry.getValue());
            if (idx < knobLen) {
                changed |= applyDial(serial, idx, color);
            } else {
                var slider = idx - knobLen;
                if (slider >= 0 && slider < sliderLen) {
                    changed |= applySlider(serial, slider, color);
                }
                if (slider >= 0 && slider < labelLen) {
                    changed |= applyLabel(serial, slider, color);
                }
            }
        }
        return changed;
    }

    /** The selected band's feedback colour for a control, or null when it is not a stepped switch / has none. */
    private static String colorFor(Commands commands) {
        return commands.getCommand(CommandAnalogBands.class).map(CommandAnalogBands::getCurrentColor).orElse(null);
    }

    private boolean applyDial(String serial, int idx, String color) {
        var wanted = isOff(color) ? null
                : new SingleKnobLightingConfig().setMode(SINGLE_KNOB_MODE.STATIC).setColor1(color);
        var current = holder.getDialOverride(serial, idx).orElse(null);
        if (Objects.equals(current == null ? null : current.getColor1(), wanted == null ? null : wanted.getColor1())) {
            return false;
        }
        holder.setDialOverride(serial, idx, wanted);
        return true;
    }

    private boolean applySlider(String serial, int idx, String color) {
        var wanted = isOff(color) ? null
                : new SingleSliderLightingConfig().setMode(SINGLE_SLIDER_MODE.STATIC).setColor1(color);
        var current = holder.getSliderOverride(serial, idx).orElse(null);
        if (Objects.equals(current == null ? null : current.getColor1(), wanted == null ? null : wanted.getColor1())) {
            return false;
        }
        holder.setSliderOverride(serial, idx, wanted);
        return true;
    }

    private boolean applyLabel(String serial, int idx, String color) {
        var wanted = isOff(color) ? null
                : new SingleSliderLabelLightingConfig().setMode(SINGLE_SLIDER_LABEL_MODE.STATIC).setColor(color);
        var current = holder.getSliderLabelOverride(serial, idx).orElse(null);
        if (Objects.equals(current == null ? null : current.getColor(), wanted == null ? null : wanted.getColor())) {
            return false;
        }
        holder.setSliderLabelOverride(serial, idx, wanted);
        return true;
    }

    /** Only a blank/absent colour means no feedback colour for this position; black (#000000) is honoured. */
    private static boolean isOff(String color) {
        return StringUtils.isBlank(color);
    }

    @Override
    public IOverrideColorProvider getOverrideColorProvider() {
        return holder;
    }
}
