package com.getpcpanel.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.getpcpanel.AppLikeMapper;
import com.getpcpanel.StubBeans;
import com.getpcpanel.device.provider.pcpanel.DescriptorFactory;
import com.getpcpanel.device.provider.pcpanel.DeviceType;
import com.getpcpanel.profile.dto.LightingConfig;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig;
import com.getpcpanel.profile.dto.SingleSliderLabelLightingConfig;
import com.getpcpanel.profile.dto.SingleSliderLightingConfig;

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

    /** Builds a save whose first knob and first slider carry {@code target} as their mute-override target. */
    private static Save saveWithMuteTarget(String target) {
        var save = new Save();
        var ds = new DeviceSave(save, DescriptorFactory.forType(DeviceType.PCPANEL_PRO));
        save.getDevices().put("serial1", ds);
        var profile = ds.getProfiles().get(0);
        var lc = profile.lightingConfig();
        lc.knobConfigs()[0].setMuteOverrideDeviceOrFollow(target);
        lc.sliderConfigs()[0].setMuteOverrideDeviceOrFollow(target);
        profile.setLightingConfig(lc);
        return save;
    }

    /** The mute-override targets of the fixture's first knob and first slider. */
    private record Targets(String knob, String slider) {
    }

    private static Targets targetsOf(Save save) {
        var lc = save.getDevices().get("serial1").getProfiles().get(0).lightingConfig();
        return new Targets(lc.knobConfigs()[0].getMuteOverrideDeviceOrFollow(), lc.sliderConfigs()[0].getMuteOverrideDeviceOrFollow());
    }

    @Test
    void theLegacyFollowWordingIsRewrittenToBlank() {
        var save = saveWithMuteTarget(LightingConfig.LEGACY_FOLLOW_TARGET);

        var migrated = SaveService.migrateMuteOverrideFollow(save);

        assertTrue(migrated, "a save still spelling the follow target out should be migrated");
        var targets = targetsOf(save);
        assertEquals("", targets.knob(), "the knob's target should be blank, which is what follow means");
        assertEquals("", targets.slider(), "sliders carry the same target property");
    }

    @Test
    void aNamedDeviceTargetIsLeftIntact() {
        var save = saveWithMuteTarget("Speakers (Realtek Audio)");

        var migrated = SaveService.migrateMuteOverrideFollow(save);

        assertFalse(migrated, "a target naming a device is not legacy wording");
        assertEquals("Speakers (Realtek Audio)", targetsOf(save).knob());
    }

    /** Every mute-override target in the save, across knobs, sliders and slider labels. */
    private static List<String> muteTargetsIn(Save save) {
        return save.getDevices().values().stream()
                   .flatMap(d -> d.getProfiles().stream())
                   .map(Profile::lightingConfig)
                   .flatMap(lc -> Stream.of(
                           Arrays.stream(lc.knobConfigs()).map(SingleKnobLightingConfig::getMuteOverrideDeviceOrFollow),
                           Arrays.stream(lc.sliderConfigs()).map(SingleSliderLightingConfig::getMuteOverrideDeviceOrFollow),
                           Arrays.stream(lc.sliderLabelConfigs()).map(SingleSliderLabelLightingConfig::getMuteOverrideDeviceOrFollow))
                                          .flatMap(s -> s))
                   .filter(Objects::nonNull)
                   .toList();
    }

    @Test
    void aRealLegacyFileHasEveryFollowTargetMigrated() throws Exception {
        StubBeans.install();
        var save = AppLikeMapper.build().readValue(
                new String(Objects.requireNonNull(getClass().getResourceAsStream("/legacy-saves/profiles-1.7.1.json")).readAllBytes(), StandardCharsets.UTF_8),
                Save.class);

        // Without the wording in the fixture this test proves nothing, so say so rather than pass empty.
        assertTrue(muteTargetsIn(save).contains(LightingConfig.LEGACY_FOLLOW_TARGET),
                "the 1.7.1 fixture no longer spells the follow target out, so this guards nothing");

        var migrated = SaveService.migrateMuteOverrideFollow(save);

        assertTrue(migrated, "a real pre-2.0 file should be migrated");
        assertFalse(muteTargetsIn(save).contains(LightingConfig.LEGACY_FOLLOW_TARGET),
                "no control may still name the follow wording as if it were a device");
    }
}
