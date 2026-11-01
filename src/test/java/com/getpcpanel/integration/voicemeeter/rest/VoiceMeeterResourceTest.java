package com.getpcpanel.integration.voicemeeter.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.voicemeeter.Voicemeeter.ControlType;
import com.getpcpanel.integration.voicemeeter.VoicemeeterVersion;

@DisplayName("VoiceMeeterResource parameter groups")
class VoiceMeeterResourceTest {

    @Test
    @DisplayName("Potato lists 8 strips and 8 buses with their dial parameters")
    void potatoDials() {
        var groups = VoiceMeeterResource.groups(VoicemeeterVersion.POTATO, false, (ct, idx) -> null);

        assertEquals(16, groups.size());
        assertEquals("Input 1", groups.getFirst().name);
        assertTrue(groups.getFirst().params.contains("Strip[0].Gain"));
        assertTrue(groups.get(5).params.contains("Strip[5].EQGain1"));
        assertEquals("Output 8", groups.getLast().name);
        assertTrue(groups.getLast().params.contains("Bus[7].Gain"));
    }

    @Test
    @DisplayName("Button groups carry the button parameters")
    void potatoButtons() {
        var groups = VoiceMeeterResource.groups(VoicemeeterVersion.POTATO, true, (ct, idx) -> null);

        assertTrue(groups.getFirst().params.contains("Strip[0].Mute"));
        assertTrue(groups.getFirst().params.contains("Strip[0].A1"));
        assertTrue(groups.getLast().params.contains("Bus[7].Mute"));
    }

    @Test
    @DisplayName("A strip label is appended to the group name")
    void labels() {
        var groups = VoiceMeeterResource.groups(VoicemeeterVersion.BANANA, false,
                (ct, idx) -> ct == ControlType.STRIP && idx == 0 ? " Mic " : "");

        assertEquals("Input 1 · Mic", groups.getFirst().name);
        assertEquals("Input 2", groups.get(1).name);
    }

    @Test
    @DisplayName("No version (not connected) gives no groups")
    void notConnected() {
        assertTrue(VoiceMeeterResource.groups(null, false, (ct, idx) -> "x").isEmpty());
    }
}
