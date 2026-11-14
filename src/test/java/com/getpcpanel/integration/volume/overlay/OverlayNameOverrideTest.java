package com.getpcpanel.integration.volume.overlay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.getpcpanel.profile.dto.KnobSetting;

/**
 * Unit tests for {@link Overlay#overlayName(KnobSetting, OverlaySlot, Supplier)} — the overlay name a user can type
 * to replace the name derived from the control's actions (issue #158), per action slot (issue #192).
 */
class OverlayNameOverrideTest {
    private static final Supplier<String> DETECTED = () -> "Spotify";

    @Test
    void usesTheOverrideWhenSet() {
        assertEquals("Music", Overlay.overlayName(setting("Music"), OverlaySlot.TURN, DETECTED));
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertEquals("Music", Overlay.overlayName(setting("  Music  "), OverlaySlot.TURN, DETECTED));
    }

    @Test
    void doesNotResolveTheDetectedNameWhenOverridden() {
        Supplier<String> exploding = () -> fail("the detected name must not be resolved when overridden");

        assertEquals("Music", Overlay.overlayName(setting("Music"), OverlaySlot.TURN, exploding));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "   " })
    void fallsBackToTheDetectedNameWhenBlank(String override) {
        assertEquals("Spotify", Overlay.overlayName(setting(override), OverlaySlot.TURN, DETECTED));
    }

    @Test
    void fallsBackToTheDetectedNameWithoutSettings() {
        assertEquals("Spotify", Overlay.overlayName(null, OverlaySlot.TURN, DETECTED));
    }

    @Test
    void eachSlotUsesItsOwnName() {
        var setting = setting("Default");
        setting.setTurnOverlayName("Volume");
        setting.setPressOverlayName("Mute");
        setting.setDblPressOverlayName("Next");
        setting.setHoldOverlayName("Deafen");
        setting.setReleaseOverlayName("Unmute");

        assertEquals("Volume", Overlay.overlayName(setting, OverlaySlot.TURN, DETECTED));
        assertEquals("Mute", Overlay.overlayName(setting, OverlaySlot.PRESS, DETECTED));
        assertEquals("Next", Overlay.overlayName(setting, OverlaySlot.DOUBLE_PRESS, DETECTED));
        assertEquals("Deafen", Overlay.overlayName(setting, OverlaySlot.HOLD, DETECTED));
        assertEquals("Unmute", Overlay.overlayName(setting, OverlaySlot.RELEASE, DETECTED));
    }

    @ParameterizedTest
    @EnumSource(OverlaySlot.class)
    void aSlotWithoutANameUsesTheControlDefault(OverlaySlot slot) {
        assertEquals("Volume", Overlay.overlayName(setting("Volume"), slot, DETECTED));
    }

    @Test
    void anUnknownSlotUsesTheControlDefault() {
        var setting = setting("Volume");
        setting.setPressOverlayName("Mute");

        assertEquals("Volume", Overlay.overlayName(setting, null, DETECTED));
    }

    @Test
    void aSlotIconFallsBackToTheControlDefault() {
        var setting = new KnobSetting();
        setting.setOverlayIcon("default.png");
        setting.setHoldOverlayIcon("hold.png");
        setting.setTurnOverlayIcon("turn.png");

        assertEquals("hold.png", OverlaySlot.icon(setting, OverlaySlot.HOLD));
        assertEquals("turn.png", OverlaySlot.icon(setting, OverlaySlot.TURN));
        assertEquals("default.png", OverlaySlot.icon(setting, OverlaySlot.PRESS));
    }

    /** Only a name typed for the button slot itself shows the overlay on every press; the default does not. */
    @Test
    void onlyAButtonSlotsOwnNameCounts() {
        var setting = setting("Volume");
        setting.setDblPressOverlayName("Next");
        setting.setTurnOverlayName("Turn");

        assertTrue(OverlaySlot.hasOwnName(setting, OverlaySlot.DOUBLE_PRESS));
        assertFalse(OverlaySlot.hasOwnName(setting, OverlaySlot.PRESS));
        assertFalse(OverlaySlot.hasOwnName(setting, OverlaySlot.TURN));
        assertFalse(OverlaySlot.hasOwnName(setting, null));
    }

    private static KnobSetting setting(String overlayName) {
        var setting = new KnobSetting();
        setting.setOverlayName(overlayName);
        return setting;
    }
}
