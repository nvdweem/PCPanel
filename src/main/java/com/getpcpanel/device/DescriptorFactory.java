package com.getpcpanel.device;

import java.util.ArrayList;
import java.util.List;

import com.getpcpanel.device.descriptor.AnalogInputSpec;
import com.getpcpanel.device.descriptor.AnalogKind;
import com.getpcpanel.device.descriptor.DeviceDescriptor;
import com.getpcpanel.device.descriptor.DigitalInputSpec;
import com.getpcpanel.device.descriptor.GlobalLightingSpec;
import com.getpcpanel.device.descriptor.LightColorModel;
import com.getpcpanel.device.descriptor.LightGroupKind;
import com.getpcpanel.device.descriptor.LightOutputSpec;

/**
 * Builds the three built-in PCPanel {@link DeviceDescriptor}s from the {@link DeviceType} catalog,
 * preserving each model's existing geometry and lighting-capability matrix so PCPanel owners get
 * exactly today's behavior with zero configuration.
 *
 * <p>{@code DeviceType} is a PCPanel-provider-internal table here; nothing outside the PCPanel
 * provider needs to read it once a descriptor exists.
 */
public final class DescriptorFactory {
    public static final String PROVIDER_ID = "pcpanel";

    private static final List<String> ELEMENT_MODES_DIAL = List.of("NONE", "STATIC", "VOLUME_GRADIENT");
    private static final List<String> ELEMENT_MODES_SLIDER = List.of("NONE", "STATIC", "STATIC_GRADIENT", "VOLUME_GRADIENT");
    private static final List<String> ELEMENT_MODES_SLIDER_LABEL = List.of("NONE", "STATIC");
    private static final List<String> ELEMENT_MODES_LOGO = List.of("NONE", "STATIC", "RAINBOW", "BREATH");

    // Index at which Pro analog inputs switch from dials to sliders (dials 0..4, sliders 5..8).
    private static final int PRO_DIAL_COUNT = 5;

    private DescriptorFactory() {
    }

    public static DeviceDescriptor forType(DeviceType type) {
        return switch (type) {
            case PCPANEL_RGB -> rgb(type);
            case PCPANEL_MINI -> mini(type);
            case PCPANEL_PRO -> pro(type);
        };
    }

    private static DeviceDescriptor rgb(DeviceType type) {
        var analogInputs = new ArrayList<AnalogInputSpec>();
        var digitalInputs = new ArrayList<DigitalInputSpec>();
        var lightOutputs = new ArrayList<LightOutputSpec>();
        for (var i = 0; i < type.getAnalogCount(); i++) {
            // RGB hardware reports knob positions in 0-100; everything downstream assumes 0-255.
            analogInputs.add(new AnalogInputSpec(i, "knob" + i, "K" + (i + 1), AnalogKind.KNOB, 0, 100, true, i));
            digitalInputs.add(new DigitalInputSpec(i, "button" + i, "B" + (i + 1), false));
            lightOutputs.add(dialLight(i));
        }
        var global = new GlobalLightingSpec(
                List.of("ALL_COLOR", "SINGLE_COLOR", "ALL_RAINBOW", "ALL_WAVE", "ALL_BREATH"),
                true, 0, 255, true);
        return new DeviceDescriptor(PROVIDER_ID, type.name(), type.getNiceName(),
                List.copyOf(analogInputs), List.copyOf(digitalInputs), List.copyOf(lightOutputs), List.of(), global);
    }

    private static DeviceDescriptor mini(DeviceType type) {
        var analogInputs = new ArrayList<AnalogInputSpec>();
        var digitalInputs = new ArrayList<DigitalInputSpec>();
        var lightOutputs = new ArrayList<LightOutputSpec>();
        for (var i = 0; i < type.getAnalogCount(); i++) {
            analogInputs.add(new AnalogInputSpec(i, "knob" + i, "K" + (i + 1), AnalogKind.KNOB, 0, 255, true, i));
            digitalInputs.add(new DigitalInputSpec(i, "button" + i, "B" + (i + 1), false));
            lightOutputs.add(dialLight(i));
        }
        var global = new GlobalLightingSpec(
                List.of("ALL_COLOR", "ALL_RAINBOW", "ALL_WAVE", "ALL_BREATH", "CUSTOM"),
                true, 0, 255, true);
        return new DeviceDescriptor(PROVIDER_ID, type.name(), type.getNiceName(),
                List.copyOf(analogInputs), List.copyOf(digitalInputs), List.copyOf(lightOutputs), List.of(), global);
    }

    private static DeviceDescriptor pro(DeviceType type) {
        var analogInputs = new ArrayList<AnalogInputSpec>();
        var digitalInputs = new ArrayList<DigitalInputSpec>();
        var lightOutputs = new ArrayList<LightOutputSpec>();
        var lightIndex = 0;
        // Dials 0..4
        for (var i = 0; i < PRO_DIAL_COUNT; i++) {
            analogInputs.add(new AnalogInputSpec(i, "knob" + i, "K" + (i + 1), AnalogKind.KNOB, 0, 255, true, lightIndex));
            lightOutputs.add(dialLight(lightIndex++));
        }
        // Buttons share the dial indices
        for (var i = 0; i < type.getButtonCount(); i++) {
            digitalInputs.add(new DigitalInputSpec(i, "button" + i, "B" + (i + 1), false));
        }
        // Sliders 5..8
        var sliderCount = type.getAnalogCount() - PRO_DIAL_COUNT;
        for (var s = 0; s < sliderCount; s++) {
            var idx = PRO_DIAL_COUNT + s;
            analogInputs.add(new AnalogInputSpec(idx, "slider" + s, "S" + (s + 1), AnalogKind.SLIDER, 0, 255, false, lightIndex));
            lightOutputs.add(new LightOutputSpec(lightIndex++, "sliderLight" + s, "S" + (s + 1),
                    LightColorModel.RGB, LightGroupKind.SLIDER, ELEMENT_MODES_SLIDER));
            lightOutputs.add(new LightOutputSpec(lightIndex++, "sliderLabel" + s, "S" + (s + 1) + " label",
                    LightColorModel.RGB, LightGroupKind.SLIDER_LABEL, ELEMENT_MODES_SLIDER_LABEL));
        }
        lightOutputs.add(new LightOutputSpec(lightIndex, "logo", "Logo",
                LightColorModel.RGB, LightGroupKind.LOGO, ELEMENT_MODES_LOGO));
        var global = new GlobalLightingSpec(
                List.of("ALL_COLOR", "ALL_RAINBOW", "ALL_WAVE", "ALL_BREATH", "CUSTOM"),
                true, 0, 255, true);
        return new DeviceDescriptor(PROVIDER_ID, type.name(), type.getNiceName(),
                List.copyOf(analogInputs), List.copyOf(digitalInputs), List.copyOf(lightOutputs), List.of(), global);
    }

    private static LightOutputSpec dialLight(int index) {
        return new LightOutputSpec(index, "dialLight" + index, "K" + (index + 1),
                LightColorModel.RGB, LightGroupKind.DIAL, ELEMENT_MODES_DIAL);
    }
}
