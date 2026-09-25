package com.getpcpanel.device;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getpcpanel.device.provider.pcpanel.DescriptorFactory;
import com.getpcpanel.device.provider.pcpanel.DeviceType;
import com.getpcpanel.device.provider.pcpanel.OutputInterpreter;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.dto.LightingConfig;

@DisplayName("Device lighting fallback")
class DeviceLightingFallbackTest {
    @Test
    void sendThatAlwaysFailsDoesNotRecurse() {
        // A lit descriptor with no DeviceType makes every OutputInterpreter send throw, the same as a
        // send to a device that is no longer connected. The fallback to the default lighting then
        // fails too; that must end there instead of retrying the default until the stack overflows.
        var device = new GenericDevice(null, new OutputInterpreter(), null, null, "pro:test", new DeviceSave(),
                DescriptorFactory.forType(DeviceType.PCPANEL_PRO));
        var config = LightingConfig.defaultLightingConfig(device.descriptor());

        assertDoesNotThrow(() -> device.setLighting(config, true));
    }
}
