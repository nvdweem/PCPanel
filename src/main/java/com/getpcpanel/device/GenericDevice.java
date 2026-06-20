package com.getpcpanel.device;

import com.getpcpanel.commands.IconService;
import com.getpcpanel.device.descriptor.AnalogInputSpec;
import com.getpcpanel.device.descriptor.DeviceDescriptor;
import com.getpcpanel.hid.OutputInterpreter;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.SaveService;

import jakarta.enterprise.event.Event;
import one.util.streamex.StreamEx;

/**
 * A descriptor-driven device with no {@link DeviceType}, no buttons and no lights. It models any
 * controller a provider describes purely as data (e.g. a Deej serial volume mixer). Analog rotation
 * values are stored in an array sized from the descriptor's highest analog index; buttons and
 * lighting are no-ops (the lightless path in {@link Device#setLighting} skips hardware output for a
 * descriptor whose {@code globalLighting} is null).
 */
public class GenericDevice extends Device {
    private final int[] rotations;

    public GenericDevice(SaveService saveService, OutputInterpreter outputInterpreter, IconService iconService,
            Event<Object> eventBus, String serialNum, DeviceSave deviceSave, DeviceDescriptor descriptor) {
        super(saveService, outputInterpreter, iconService, eventBus, serialNum, deviceSave, descriptor);
        var maxIndex = StreamEx.of(descriptor.analogInputs()).mapToInt(AnalogInputSpec::index).max().orElse(-1);
        rotations = new int[maxIndex + 1];
    }

    @Override
    public void setKnobRotation(int knob, int rotation) {
        if (knob >= 0 && knob < rotations.length) {
            rotations[knob] = rotation;
        }
    }

    @Override
    public int getKnobRotation(int knob) {
        return knob >= 0 && knob < rotations.length ? rotations[knob] : 0;
    }

    @Override
    public void setButtonPressed(int button, boolean pressed) {
        // Generic devices (Deej) have no buttons.
    }
}
