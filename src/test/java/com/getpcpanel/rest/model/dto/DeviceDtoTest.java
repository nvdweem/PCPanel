package com.getpcpanel.rest.model.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.getpcpanel.device.DescriptorFactory;
import com.getpcpanel.device.DeviceType;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.Save;

class DeviceDtoTest {
    @Test
    void fromPersistedProDescriptorBuildsDisconnectedDtoWithRightCounts() {
        var save = new Save();
        var descriptor = DescriptorFactory.forType(DeviceType.PCPANEL_PRO);
        var ds = new DeviceSave(save, descriptor);

        var dto = DeviceDto.from("serialA", ds, descriptor);

        assertFalse(dto.connected(), "a persisted-but-not-live device must report connected=false");
        assertEquals("serialA", dto.serial());
        assertEquals(ds.getDisplayName(), dto.displayName());
        assertEquals(DeviceType.PCPANEL_PRO, dto.deviceType());
        // Pro: 5 dials + 4 sliders analog inputs, 5 buttons, a logo light.
        assertEquals(9, dto.analogCount());
        assertEquals(5, dto.buttonCount());
        assertTrue(dto.hasLogoLed());
        assertSame(descriptor, dto.descriptor());
    }

    @Test
    void fromNullCapabilitiesFallsBackGracefully() {
        var ds = new DeviceSave();
        ds.setDisplayName("pcpanel1");

        var dto = DeviceDto.from("serialB", ds, null);

        assertFalse(dto.connected());
        assertEquals(0, dto.analogCount());
        assertEquals(0, dto.buttonCount());
        assertFalse(dto.hasLogoLed());
        assertNull(dto.deviceType());
        assertNull(dto.descriptor());
    }
}
