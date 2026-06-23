package com.getpcpanel.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.CommandsType;
import com.getpcpanel.commands.command.CommandProfile;
import com.getpcpanel.profile.dto.LightingConfig;
import com.getpcpanel.profile.dto.LightingConfig.LightingMode;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig.SINGLE_KNOB_MODE;

class BaseLayerServiceTest {
    private static Commands cmds(String profile) {
        return new Commands(List.of(new CommandProfile(profile)), CommandsType.allAtOnce);
    }

    private static Profile profile(String name) {
        var p = new Profile();
        p.setName(name);
        return p;
    }

    // ── command fallback ─────────────────────────────────────────────────────────
    @Test
    void dialFallsBackToBaseWhenActiveHasNoCommand() {
        var active = profile("active");
        var base = profile("base");
        base.setDialData(0, cmds("baseProfile"));

        assertEquals("baseProfile", profileNameOf(BaseLayerService.effectiveDial(active, base, 0)));
    }

    @Test
    void activeCommandWinsOverBase() {
        var active = profile("active");
        var base = profile("base");
        active.setDialData(0, cmds("activeProfile"));
        base.setDialData(0, cmds("baseProfile"));

        assertEquals("activeProfile", profileNameOf(BaseLayerService.effectiveDial(active, base, 0)));
    }

    @Test
    void dialIsNullWhenNeitherProfileHasACommand() {
        assertNull(BaseLayerService.effectiveDial(profile("active"), profile("base"), 9));
    }

    @Test
    void releaseButtonFallsBackToBaseWhenActiveHasNoCommand() {
        var active = profile("active");
        var base = profile("base");
        base.setReleaseButtonData(0, cmds("baseProfile"));

        assertEquals("baseProfile", profileNameOf(BaseLayerService.effectiveReleaseButton(active, base, 0)),
                "a push-to-talk release on the base layer must still fire from another profile");
    }

    @Test
    void activeReleaseButtonWinsOverBase() {
        var active = profile("active");
        var base = profile("base");
        active.setReleaseButtonData(0, cmds("activeProfile"));
        base.setReleaseButtonData(0, cmds("baseProfile"));

        assertEquals("activeProfile", profileNameOf(BaseLayerService.effectiveReleaseButton(active, base, 0)));
    }

    @Test
    void effectiveDialDataMergesBaseUnderActive() {
        var active = profile("active");
        var base = profile("base");
        base.setDialData(0, cmds("base0"));
        base.setDialData(1, cmds("base1"));
        active.setDialData(1, cmds("active1"));

        var merged = BaseLayerService.effectiveDialData(active, base);

        assertEquals("base0", profileNameOf(merged.get(0)), "base-only control comes from the base layer");
        assertEquals("active1", profileNameOf(merged.get(1)), "active wins where configured");
    }

    // ── lighting fallback ────────────────────────────────────────────────────────
    @Test
    void noneKnobFallsBackToBaseColourAndActiveIsNotMutated() {
        var active = custom();
        active.knobConfigs()[1].setMode(SINGLE_KNOB_MODE.STATIC).setColor1("#FF0000"); // knob 0 stays NONE
        var base = custom();
        base.knobConfigs()[0].setMode(SINGLE_KNOB_MODE.STATIC).setColor1("#0000FF");

        var merged = BaseLayerService.mergeLighting(active, base);

        assertEquals(SINGLE_KNOB_MODE.STATIC, merged.knobConfigs()[0].getMode());
        assertEquals("#0000FF", merged.knobConfigs()[0].getColor1(), "off knob takes the base-layer colour");
        assertEquals("#FF0000", merged.knobConfigs()[1].getColor1(), "configured knob is untouched");
        assertEquals(SINGLE_KNOB_MODE.NONE, active.knobConfigs()[0].getMode(), "merge must not mutate the active config");
    }

    @Test
    void globalLightingModePassesThroughUnmerged() {
        var active = new LightingConfig(5, 4);
        active.setLightingMode(LightingMode.ALL_COLOR);
        var base = custom();
        base.knobConfigs()[0].setMode(SINGLE_KNOB_MODE.STATIC).setColor1("#0000FF");

        assertSame(active, BaseLayerService.mergeLighting(active, base), "only CUSTOM mode has per-control off slots to fill");
    }

    private static LightingConfig custom() {
        var lc = new LightingConfig(5, 4); // Pro: 5 knobs, 4 sliders
        lc.setLightingMode(LightingMode.CUSTOM);
        return lc;
    }

    private static String profileNameOf(Commands c) {
        return c.getCommand(CommandProfile.class).map(CommandProfile::getProfile).orElse(null);
    }
}
