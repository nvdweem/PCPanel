package com.getpcpanel.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.getpcpanel.commands.curve.Curve;
import com.getpcpanel.profile.dto.KnobSetting;

/**
 * Where a waiting control takes over, as a control position: what the overlay marks. A level is the target's own
 * (after the control's curve and trim); the position is where the control has to be to produce it.
 */
class SoftTakeoverPositionTest {
    @Test
    void onALinearCurveThePositionIsTheLevel() {
        var dial = new DialValue(null, Curve.LINEAR, 0);
        assertEquals(204, SoftTakeover.positionFor(dial, null, 0.8f), 1);
        assertEquals(0, SoftTakeover.positionFor(dial, null, 0f));
        assertEquals(255, SoftTakeover.positionFor(dial, null, 1f));
    }

    @Test
    void onACurveThePositionIsWhereTheCurveGivesTheLevel() {
        // A control at 80% on x² sets 64%: a target at 64% is taken over at 80%, not at 64%.
        var dial = new DialValue(null, x -> x * x, 0);
        assertEquals(204, SoftTakeover.positionFor(dial, null, 0.64f), 1);
    }

    @Test
    void trimIsTakenIntoAccount() {
        var setting = new KnobSetting();
        setting.setMinTrim(50); // the control's whole travel covers 50%..100%
        var dial = new DialValue(setting, Curve.LINEAR, 0);
        assertEquals(0, SoftTakeover.positionFor(dial, null, 0.5f), 1);
        assertEquals(128, SoftTakeover.positionFor(dial, null, 0.75f), 1);
    }
}
