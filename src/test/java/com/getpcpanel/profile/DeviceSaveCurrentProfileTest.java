package com.getpcpanel.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getpcpanel.profile.dto.LightingConfig;

/**
 * The current-profile pointer is not just an internal detail: it is the name the UI is handed as the
 * active profile, and the one it puts in the URL of every assignment it saves. Those endpoints resolve
 * the profile strictly, so a pointer naming a profile that is not there reads perfectly — the app falls
 * back to the first one everywhere it looks the profile up itself — while every save 404s, for as long
 * as the save file says so (issue #150). These pin that the fallback also repairs the pointer.
 */
@DisplayName("The current-profile pointer always names a profile that exists")
class DeviceSaveCurrentProfileTest {
    @Test
    @DisplayName("a pointer naming a deleted profile is repointed at the first one")
    void repointsAfterTheNamedProfileIsGone() {
        var save = saveWith("gaming", "music");
        save.setCurrentProfileName("gaming");

        save.getProfiles().removeIf(p -> "gaming".equals(p.getName()));

        assertEquals("music", save.ensureCurrentProfile(defaultLighting()).getName());
        assertEquals("music", save.getCurrentProfileName(),
                "the pointer the UI is handed must name the profile the app actually resolved");
    }

    @Test
    @DisplayName("a pointer that never named anything is repointed at the first one")
    void repointsWhenThePointerNamesNothing() {
        var save = saveWith("gaming", "music");
        save.setCurrentProfileName("never-existed");

        assertEquals("gaming", save.ensureCurrentProfile(defaultLighting()).getName());
        assertEquals("gaming", save.getCurrentProfileName());
    }

    @Test
    @DisplayName("a pointer that already resolves is left alone")
    void keepsAValidPointer() {
        var save = saveWith("gaming", "music");
        save.setCurrentProfileName("music");

        assertEquals("music", save.ensureCurrentProfile(defaultLighting()).getName());
        assertEquals("music", save.getCurrentProfileName());
    }

    @Test
    @DisplayName("an empty profile list still gets a profile, and the pointer names it")
    void createsAProfileWhenThereAreNone() {
        var save = new DeviceSave();
        save.setCurrentProfileName("never-existed");

        var profile = save.ensureCurrentProfile(defaultLighting());

        assertTrue(save.getProfiles().contains(profile));
        assertEquals(profile.getName(), save.getCurrentProfileName());
    }

    private static DeviceSave saveWith(String... names) {
        var save = new DeviceSave();
        var profiles = new ArrayList<Profile>();
        Arrays.stream(names).forEach(name -> profiles.add(new Profile(name, defaultLighting())));
        save.setProfiles(profiles);
        return save;
    }

    private static Supplier<LightingConfig> defaultLighting() {
        return () -> LightingConfig.createAllColor("#0065FF");
    }
}
