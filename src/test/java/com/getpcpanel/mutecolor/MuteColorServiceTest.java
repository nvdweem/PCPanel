package com.getpcpanel.mutecolor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.CommandsType;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.profile.dto.LightingConfig;
import com.getpcpanel.profile.dto.LightingConfig.LightingMode;
import com.getpcpanel.wavelink.command.CommandWaveLinkChangeLevel;
import com.getpcpanel.wavelink.command.WaveLinkCommandTarget;

/**
 * Exercises the mute-override mapping for the exact reported scenario: a Pro slider bound to a Wave
 * Link channel volume, with a muted colour, should get a STATIC override of that colour when the
 * channel is muted (and lose it when unmuted). Uses a stub resolver so the orchestration — slider
 * indexing, blank-target → FOLLOW, override apply/clear — is what's under test.
 */
class MuteColorServiceTest {
    private static Commands waveLinkChannelVolume(String channelId) {
        Command cmd = new CommandWaveLinkChangeLevel(WaveLinkCommandTarget.Channel, channelId, null, null);
        return new Commands(List.of(cmd), CommandsType.allAtOnce);
    }

    private static MuteColorService serviceWithMuteState(boolean muted) {
        var service = new MuteColorService();
        service.resolvers = List.of((command, target) -> Optional.of(muted));
        return service;
    }

    private static LightingConfig proCustomWithSliderMuteColor(String color) {
        var lc = new LightingConfig(5, 4); // Pro: knobs at analog index 0-4, sliders at 5-8
        lc.setLightingMode(LightingMode.CUSTOM);
        lc.sliderConfigs()[0].setMuteOverrideColor(color);
        return lc;
    }

    @Test
    void mutedWaveLinkSliderGetsOverrideColor() {
        var service = serviceWithMuteState(true);
        var lc = proCustomWithSliderMuteColor("#FF0000");
        var dialData = Map.of(5, waveLinkChannelVolume("music")); // slider 0 == analog index 5

        var changed = service.applyOverrides("serial", lc, dialData);

        assertTrue(changed, "muting should apply an override");
        var override = service.getOverrideColorProvider().getSliderOverride("serial", 0);
        assertTrue(override.isPresent(), "slider 0 should have a mute override");
        assertEquals("#FF0000", override.get().getColor1());
    }

    @Test
    void unmutedWaveLinkSliderHasNoOverride() {
        var service = serviceWithMuteState(false);
        var lc = proCustomWithSliderMuteColor("#FF0000");
        var dialData = Map.of(5, waveLinkChannelVolume("music"));

        var changed = service.applyOverrides("serial", lc, dialData);

        assertFalse(changed, "an unmuted channel should not apply an override");
        assertTrue(service.getOverrideColorProvider().getSliderOverride("serial", 0).isEmpty());
    }

    @Test
    void blackIsAUsableMuteColourNotTreatedAsOff() {
        var service = serviceWithMuteState(true);
        var lc = proCustomWithSliderMuteColor("#000000");
        var dialData = Map.of(5, waveLinkChannelVolume("music"));

        var changed = service.applyOverrides("serial", lc, dialData);

        assertTrue(changed, "black is an explicit colour, not the off state");
        var override = service.getOverrideColorProvider().getSliderOverride("serial", 0);
        assertTrue(override.isPresent(), "a muted channel with a black mute colour gets a (black) override");
        assertEquals("#000000", override.get().getColor1());
    }

    @Test
    void unmutingClearsAPreviouslyAppliedOverride() {
        var service = new MuteColorService();
        var lc = proCustomWithSliderMuteColor("#FF0000");
        var dialData = Map.of(5, waveLinkChannelVolume("music"));

        service.resolvers = List.of((command, target) -> Optional.of(true));
        service.applyOverrides("serial", lc, dialData);
        assertTrue(service.getOverrideColorProvider().getSliderOverride("serial", 0).isPresent());

        service.resolvers = List.of((command, target) -> Optional.of(false));
        var changed = service.applyOverrides("serial", lc, dialData);

        assertTrue(changed, "unmuting should clear the override");
        assertTrue(service.getOverrideColorProvider().getSliderOverride("serial", 0).isEmpty());
    }
}
