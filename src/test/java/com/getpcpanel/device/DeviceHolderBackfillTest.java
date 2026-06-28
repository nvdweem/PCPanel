package com.getpcpanel.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.getpcpanel.device.provider.pcpanel.DescriptorFactory;
import com.getpcpanel.device.provider.pcpanel.DeviceType;
import com.getpcpanel.profile.DeviceSave;

class DeviceHolderBackfillTest {
    @Test
    void backfillPopulatesIdentityAndCapabilitiesFromDescriptor() {
        var descriptor = DescriptorFactory.forType(DeviceType.PCPANEL_PRO);
        // A legacy save migrated to providerId only; no kind/capabilities yet.
        var ds = new DeviceSave();
        ds.setProviderId(DescriptorFactory.PROVIDER_ID);

        var changed = DeviceHolder.backfillIdentity(ds, descriptor);

        assertTrue(changed, "back-fill should report a change when kind/capabilities are missing");
        assertEquals(DescriptorFactory.PROVIDER_ID, ds.getProviderId());
        assertEquals(DeviceType.PCPANEL_PRO.name(), ds.getDeviceKindId());
        assertEquals(descriptor, ds.getCapabilities());
    }

    @Test
    void backfillIsNoOpWhenAlreadyMatching() {
        var descriptor = DescriptorFactory.forType(DeviceType.PCPANEL_MINI);
        var ds = new DeviceSave();
        ds.setProviderId(descriptor.providerId());
        ds.setDeviceKindId(descriptor.deviceKindId());
        ds.setCapabilities(descriptor);

        assertFalse(DeviceHolder.backfillIdentity(ds, descriptor),
                "back-fill should be a no-op when the save already matches the descriptor");
    }
}
