package com.getpcpanel.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getpcpanel.device.descriptor.AnalogInputSpec;
import com.getpcpanel.device.descriptor.AnalogKind;
import com.getpcpanel.device.descriptor.DeviceDescriptor;
import com.getpcpanel.profile.DeviceSave;

@DisplayName("GenericDevice (lightless, descriptor-only)")
class GenericDeviceTest {
    private static DeviceDescriptor deejDescriptor(int sliders) {
        var inputs = new java.util.ArrayList<AnalogInputSpec>();
        for (var i = 0; i < sliders; i++) {
            inputs.add(new AnalogInputSpec(i, "slider" + i, "S" + (i + 1), AnalogKind.SLIDER, 0, 1023, false, null));
        }
        return new DeviceDescriptor("deej", "deej", "Deej", List.copyOf(inputs), List.of(), List.of(), List.of(), null);
    }

    private static GenericDevice device(DeviceDescriptor descriptor) {
        // Null collaborators are fine for the behaviour under test: no DeviceType is required, and
        // the lightless path never touches the OutputInterpreter.
        return new GenericDevice(null, null, null, null, "deej:COM3", new DeviceSave(), descriptor);
    }

    @Test
    void hasNoDeviceType() {
        assertNull(device(deejDescriptor(3)).deviceType(), "a descriptor-only device has no DeviceType");
    }

    @Test
    void storesAndRetrievesAnalogValues() {
        var d = device(deejDescriptor(3));
        d.setKnobRotation(0, 10);
        d.setKnobRotation(2, 255);
        assertEquals(10, d.getKnobRotation(0));
        assertEquals(0, d.getKnobRotation(1));
        assertEquals(255, d.getKnobRotation(2));
    }

    @Test
    void outOfRangeIndicesAreIgnored() {
        var d = device(deejDescriptor(2));
        assertDoesNotThrow(() -> d.setKnobRotation(99, 5));
        assertEquals(0, d.getKnobRotation(99));
        assertEquals(0, d.getKnobRotation(-1));
    }

    @Test
    void setButtonPressedIsNoOp() {
        var d = device(deejDescriptor(2));
        assertDoesNotThrow(() -> d.setButtonPressed(0, true));
    }

    @Test
    void setLightingIsNoOpForLightlessDevice() {
        var d = device(deejDescriptor(2));
        // No globalLighting in the descriptor => the lighting path must short-circuit (no NPE on the
        // null OutputInterpreter).
        assertDoesNotThrow(() -> d.setLighting(null, true));
    }
}
