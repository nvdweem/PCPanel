package com.getpcpanel.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.getpcpanel.device.provider.pcpanel.DescriptorFactory;
import com.getpcpanel.device.DeviceType;

class SaveServiceMigrationTest {
    @Test
    void legacyDeviceSaveGetsPcpanelProviderId() {
        var save = new Save();
        // A legacy entry: no provider identity at all (as it would deserialize from an old file).
        var legacy = new DeviceSave();
        save.getDevices().put("serial1", legacy);

        var migrated = SaveService.migrateProviderIds(save);

        assertTrue(migrated, "migration should report a change for a legacy entry");
        assertEquals(DescriptorFactory.PROVIDER_ID, legacy.getProviderId());
        // deviceKindId / capabilities were never stored, so they stay null for connect-time back-fill.
        assertNull(legacy.getDeviceKindId());
        assertNull(legacy.getCapabilities());
    }

    @Test
    void alreadyIdentifiedDeviceSaveIsLeftIntact() {
        var save = new Save();
        var descriptor = DescriptorFactory.forType(DeviceType.PCPANEL_PRO);
        var ds = new DeviceSave(save, descriptor);
        save.getDevices().put("serial1", ds);

        var migrated = SaveService.migrateProviderIds(save);

        assertFalse(migrated, "an already-identified entry should not be migrated");
        assertEquals(DescriptorFactory.PROVIDER_ID, ds.getProviderId());
        assertEquals(DeviceType.PCPANEL_PRO.name(), ds.getDeviceKindId());
    }
}
