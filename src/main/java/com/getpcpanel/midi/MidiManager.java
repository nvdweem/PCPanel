package com.getpcpanel.midi;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.getpcpanel.device.DeviceType;
import com.getpcpanel.hid.DeviceCommunicationHandler.ButtonPressEvent;
import com.getpcpanel.hid.DeviceCommunicationHandler.KnobRotateEvent;
import com.getpcpanel.hid.DeviceScanner.DeviceConnectedEvent;
import com.getpcpanel.hid.DeviceScanner.DeviceDisconnectedEvent;
import com.getpcpanel.hid.OutputInterpreter;
import com.getpcpanel.profile.LightingConfig;
import com.getpcpanel.profile.SingleKnobLightingConfig;
import com.getpcpanel.profile.SingleKnobLightingConfig.SINGLE_KNOB_MODE;
import com.getpcpanel.profile.SingleLogoLightingConfig;
import com.getpcpanel.profile.SingleLogoLightingConfig.SINGLE_LOGO_MODE;
import com.getpcpanel.profile.SingleSliderLightingConfig;
import com.getpcpanel.profile.SingleSliderLightingConfig.SINGLE_SLIDER_MODE;
import com.getpcpanel.util.Util;
import com.getpcpanel.util.coloroverride.ColorOverrideHolder;
import com.getpcpanel.util.coloroverride.IOverrideColorProvider;
import com.getpcpanel.util.coloroverride.IOverrideColorProviderProvider;

import javafx.scene.paint.Color;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@RequiredArgsConstructor
public class MidiManager implements IOverrideColorProviderProvider {
    private final MidiService midiService;
    private final OutputInterpreter outputInterpreter;
    private final ColorOverrideHolder overrideHolder = new ColorOverrideHolder();

    // Maps serial number to DeviceType for MIDI port management
    private final Map<String, DeviceType> connectedDevices = new ConcurrentHashMap<>();

    @Override
    public IOverrideColorProvider getOverrideColorProvider() {
        return overrideHolder;
    }

    // Mapping (Channel 0):
    //   Knobs/Sliders -> CC 1-16
    //   Buttons       -> Note 1-16 (Aligned with Knobs 1-5)
    //
    // Mapping (Channel 1):
    //   CC 0: Logo Indicator
    //   CC 1-16: Control Indicator (Knobs followed by Sliders)

    @EventListener
    public void onDeviceConnected(DeviceConnectedEvent event) {
        var serialNum = event.serialNum();
        var type = event.deviceType();
        connectedDevices.put(serialNum, type);

        var portName = String.format("PCPanel %s (%s)", type.getNiceName(), serialNum);
        midiService.createPort(serialNum, portName, this::handleIncomingMidi);
        log.info("Created MIDI port for connected device: {}", portName);
    }

    @EventListener
    public void onDeviceDisconnected(DeviceDisconnectedEvent event) {
        var serialNum = event.serialNum();
        midiService.closePort(serialNum);
        connectedDevices.remove(serialNum);
        overrideHolder.setLogoOverride(serialNum, null); // Clear logo override
        // Note: overrideHolder doesn't have a clearDevice method, but we can set nulls
        // or we can rely on connectedDevices check in updateLighting. 
        // Better to clear if possible.
        log.info("Closed MIDI port for disconnected device: {}", serialNum);
    }

    @EventListener
    public void onKnobRotate(KnobRotateEvent event) {
        // Send CC message for knob/slider change
        // CC number = knob index + 1 (Logo is 0), channel 0
        // PCPanel values are 0-255. MIDI is 0-127.
        var midiValue = (int) Math.round((event.value() / 255.0) * 127.0);
        midiService.sendControlChange(event.serialNum(), 0, event.knob() + 1, midiValue);
    }

    @EventListener
    public void onButtonPress(ButtonPressEvent event) {
        // Send Note message for button press
        // Note number = button index + 1 (to align with knob CCs 1-5), channel 0
        if (event.pressed()) {
            midiService.sendNoteOn(event.serialNum(), 0, event.button() + 1, 127);
        } else {
            midiService.sendNoteOff(event.serialNum(), 0, event.button() + 1, 0);
        }
    }

    private void handleIncomingMidi(String serialNum, byte[] data) {
        if (data.length < 3) {
            return;
        }

        var status = data[0] & 0xFF;
        var command = status & 0xF0;
        var channel = status & 0x0F;
        var data1 = data[1] & 0xFF;
        var data2 = data[2] & 0xFF;

        if (command == 0xB0 && channel == 1) { // Control Change on Channel 1
            updateLighting(serialNum, data1, data2);
        }
    }

    private void updateLighting(String serialNum, int cc, int value) {
        var type = connectedDevices.get(serialNum);
        if (type == null) {
            return;
        }

        // MIDI value is 0-127. Color component is 0.0 to 1.0.
        var normValue = value / 127.0;

        var changed = false;

        if (cc == 0) { // Logo Indicator
            changed = updateLogoIndicator(serialNum, normValue);
        } else if (cc >= 1 && cc <= 16) { // Indicators for Knobs/Sliders
            changed = updateIndicator(serialNum, cc - 1, normValue);
        }

        if (changed) {
            // We need a LightingConfig just to trigger the update. 
            // The OutputInterpreter will use the overrideHolder via OverrideColorService.
            // Using a dummy/empty config is fine as long as DT is correct.
            var dummy = new LightingConfig(type.getAnalogCount(), type.getAnalogCount() - type.getButtonCount());
            outputInterpreter.sendLightingConfig(serialNum, type, dummy, true);
        }
    }

    private boolean updateIndicator(String serialNum, int idx, double normValue) {
        var type = connectedDevices.get(serialNum);
        var knobCount = type.getButtonCount();
        var sliderCount = type.getAnalogCount() - knobCount;

        if (idx < knobCount) {
            var kc = overrideHolder.getDialOverride(serialNum, idx)
                                   .orElse(new SingleKnobLightingConfig());
            // Ensure mode is set if it was NONE
            if (kc.getMode() == SINGLE_KNOB_MODE.NONE) {
                kc.setMode(SINGLE_KNOB_MODE.STATIC);
                kc.setColor1(Util.formatHexString(Color.WHITE));
            }

            var changed = updateSingleIndicator(kc.getMode(), kc.getColor1(), kc.getColor2(), normValue,
                    kc::setColor1FromColor,
                    kc::setColor2FromColor);
            if (changed) {
                overrideHolder.setDialOverride(serialNum, idx, kc);
            }
            return changed;
        } else {
            var sliderIdx = idx - knobCount;
            if (sliderIdx < sliderCount) {
                var sc = overrideHolder.getSliderOverride(serialNum, sliderIdx)
                                       .orElse(new SingleSliderLightingConfig());
                if (sc.getMode() == SINGLE_SLIDER_MODE.NONE) {
                    sc.setMode(SINGLE_SLIDER_MODE.STATIC);
                    sc.setColor1(Util.formatHexString(Color.WHITE));
                }

                var changed = updateSingleIndicator(sc.getMode(), sc.getColor1(), sc.getColor2(), normValue,
                        sc::setColor1FromColor,
                        sc::setColor2FromColor);
                if (changed) {
                    overrideHolder.setSliderOverride(serialNum, sliderIdx, sc);
                }
                return changed;
            }
        }
        return false;
    }

    private boolean updateLogoIndicator(String serialNum, double normValue) {
        var lc = overrideHolder.getLogoOverride(serialNum)
                               .orElse(new SingleLogoLightingConfig());
        if (lc.getMode() == SINGLE_LOGO_MODE.NONE) {
            lc.setMode(SINGLE_LOGO_MODE.STATIC);
            lc.setColor(Color.WHITE);
        }

        if (lc.getMode() == SINGLE_LOGO_MODE.STATIC) {
            var baseColor = Util.parseColor(lc.getColor()).orElse(Color.WHITE);
            lc.setColor(interpolateColor(Color.BLACK, baseColor, normValue));
            overrideHolder.setLogoOverride(serialNum, lc);
            return true;
        }
        return false;
    }

    private <T extends Enum<T>> boolean updateSingleIndicator(T mode, String color1Str, String color2Str, double normValue,
            Consumer<Color> setColor1, Consumer<Color> setColor2) {
        var modeName = mode.name();
        if (modeName.contains("GRADIENT")) {
            var c1 = Util.parseColor(color1Str).orElse(Color.BLACK);
            var c2 = Util.parseColor(color2Str).orElse(Color.WHITE);
            var target = interpolateColor(c1, c2, normValue);
            setColor1.accept(target);
            setColor2.accept(target);
            return true;
        } else if ("STATIC".equals(modeName)) {
            var baseColor = Util.parseColor(color1Str).orElse(Color.WHITE);
            var target = interpolateColor(Color.BLACK, baseColor, normValue);
            setColor1.accept(target);
            return true;
        }
        return false;
    }

    private Color interpolateColor(Color c1, Color c2, double ratio) {
        var r = c1.getRed() + (c2.getRed() - c1.getRed()) * ratio;
        var g = c1.getGreen() + (c2.getGreen() - c1.getGreen()) * ratio;
        var b = c1.getBlue() + (c2.getBlue() - c1.getBlue()) * ratio;
        return new Color(r, g, b, 1.0);
    }
}
