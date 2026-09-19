package com.getpcpanel.integration.volume.overlay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.getpcpanel.profile.dto.KnobSetting;

/**
 * Unit tests for {@link Overlay#overlayName(KnobSetting, Supplier)} — the per-control overlay name a user
 * can type to replace the name derived from the control's actions (issue #158).
 */
class OverlayNameOverrideTest {
    private static final Supplier<String> DETECTED = () -> "Spotify";

    @Test
    void usesTheOverrideWhenSet() {
        assertEquals("Music", Overlay.overlayName(setting("Music"), DETECTED));
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertEquals("Music", Overlay.overlayName(setting("  Music  "), DETECTED));
    }

    @Test
    void doesNotResolveTheDetectedNameWhenOverridden() {
        Supplier<String> exploding = () -> fail("the detected name must not be resolved when overridden");

        assertEquals("Music", Overlay.overlayName(setting("Music"), exploding));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "   " })
    void fallsBackToTheDetectedNameWhenBlank(String override) {
        assertEquals("Spotify", Overlay.overlayName(setting(override), DETECTED));
    }

    @Test
    void fallsBackToTheDetectedNameWithoutSettings() {
        assertEquals("Spotify", Overlay.overlayName(null, DETECTED));
    }

    private static KnobSetting setting(String overlayName) {
        var setting = new KnobSetting();
        setting.setOverlayName(overlayName);
        return setting;
    }
}
