package com.getpcpanel.commands.curve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.getpcpanel.profile.dto.CurveDefinition;
import com.getpcpanel.profile.dto.CurveMode;
import com.getpcpanel.profile.dto.CurvePoint;

/**
 * Resolution turns the id stored on a control into something evaluable. The saved library is consulted
 * before the built-in defaults, which is what lets a user retune the built-in Logarithmic for every
 * control at once; removing their entry restores the default.
 */
class CurvesTest {
    private static CurveDefinition amount(String id, int amount) {
        return new CurveDefinition(id, id, CurveMode.amount, amount, List.of());
    }

    @Test
    void noCurveIsLinear() {
        assertSame(Curve.LINEAR, Curves.resolve(null, List.of()));
    }

    @Test
    void blankCurveIsLinear() {
        assertSame(Curve.LINEAR, Curves.resolve("", List.of()));
    }

    @Test
    void builtInLinearIsStraightThrough() {
        var curve = Curves.resolve(Curves.LINEAR_ID, List.of());
        for (var i = 0; i <= 100; i++) {
            assertEquals(i / 100d, curve.apply(i / 100d), 1e-12);
        }
    }

    @Test
    void builtInLogarithmicIsTheHistoricTaper() {
        var curve = Curves.resolve(Curves.LOGARITHMIC_ID, List.of());
        var expected = new AmountCurve(AmountCurve.LOG_AMOUNT);
        for (var i = 0; i <= 255; i++) {
            assertEquals(expected.apply(i / 255d), curve.apply(i / 255d), 1e-12);
        }
    }

    @Test
    void anUnknownIdFallsBackToLinearInsteadOfThrowing() {
        assertSame(Curve.LINEAR, Curves.resolve("deleted-by-the-user", List.of(amount("other", 20))));
    }

    @Test
    void aSavedCurveResolvesByItsId() {
        var saved = List.of(amount("mine", 25));
        var curve = Curves.resolve("mine", saved);
        assertEquals(new AmountCurve(25).apply(0.5), curve.apply(0.5), 1e-12);
    }

    @Test
    void aSavedCurveOverridesTheBuiltInOfTheSameId() {
        var retuned = List.of(amount(Curves.LOGARITHMIC_ID, 20));
        var curve = Curves.resolve(Curves.LOGARITHMIC_ID, retuned);
        assertEquals(new AmountCurve(20).apply(0.5), curve.apply(0.5), 1e-12);
        assertNotEquals(new AmountCurve(AmountCurve.LOG_AMOUNT).apply(0.5), curve.apply(0.5), 1e-9);
    }

    @Test
    void pointsModeUsesThePointsAndAmountModeIgnoresThem() {
        var points = List.of(new CurvePoint(0, 0), new CurvePoint(0.5, 0.9), new CurvePoint(1, 1));
        var asPoints = new CurveDefinition("p", "p", CurveMode.points, 50, points);
        var asAmount = new CurveDefinition("a", "a", CurveMode.amount, 50, points);

        assertEquals(0.9, Curves.of(asPoints).apply(0.5), 1e-9);
        assertEquals(new AmountCurve(50).apply(0.5), Curves.of(asAmount).apply(0.5), 1e-12);
    }

    @Test
    void aPointsCurveWithoutEnoughPointsFallsBackToLinear() {
        var broken = new CurveDefinition("p", "p", CurveMode.points, 0, List.of());
        assertEquals(0.42, Curves.of(broken).apply(0.42), 1e-12);
    }

    @Test
    void theBuiltInsAreOfferedInTheLibrary() {
        var ids = Curves.BUILT_INS.stream().map(CurveDefinition::id).toList();
        assertEquals(List.of(Curves.LINEAR_ID, Curves.LOGARITHMIC_ID), ids);
        assertTrue(Curves.isBuiltIn(Curves.LOGARITHMIC_ID));
        assertTrue(!Curves.isBuiltIn("mine"));
    }
}
