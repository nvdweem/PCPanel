package com.getpcpanel.commands.curve;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.profile.dto.CurveDefinition;
import com.getpcpanel.profile.dto.CurveMode;

/**
 * Turns the curve id stored on a control into something evaluable.
 *
 * <p>The saved library is consulted before {@link #BUILT_INS}, so a saved entry carrying a built-in id
 * retunes that built-in for every control referencing it at once — the "just a global setting" case —
 * and dropping that entry restores the default. An id that resolves to nothing evaluates linear rather
 * than throwing, so a curve deleted while controls still point at it degrades instead of breaking input.
 *
 * <p>Static and side-effect-free so resolution is unit-testable without the save file.
 */
public final class Curves {
    public static final String LINEAR_ID = "linear";
    public static final String LOGARITHMIC_ID = "logarithmic";

    /** The curves always offered, in the order the library lists them. */
    public static final List<CurveDefinition> BUILT_INS = List.of(
            new CurveDefinition(LINEAR_ID, "Linear", CurveMode.amount, 0, List.of()),
            new CurveDefinition(LOGARITHMIC_ID, "Logarithmic", CurveMode.amount, AmountCurve.LOG_AMOUNT, List.of()));

    private Curves() {
    }

    public static boolean isBuiltIn(@Nullable String id) {
        return BUILT_INS.stream().anyMatch(builtIn -> builtIn.id().equals(id));
    }

    /** The saved library plus every built-in the user has not overridden, built-ins first. */
    public static List<CurveDefinition> library(@Nullable List<CurveDefinition> saved) {
        var stored = saved == null ? List.<CurveDefinition>of() : saved;
        return Stream.concat(
                BUILT_INS.stream().map(builtIn -> find(builtIn.id(), stored).orElse(builtIn)),
                stored.stream().filter(curve -> !isBuiltIn(curve.id()))).toList();
    }

    /**
     * The half of a submitted library worth storing: the user's own curves, plus any built-in they have
     * actually changed. Storing an untouched built-in would freeze today's default into the save file and
     * leave nothing for "reset to default" to fall back to.
     */
    public static List<CurveDefinition> userCurves(@Nullable List<CurveDefinition> submitted) {
        if (submitted == null) {
            return List.of();
        }
        return submitted.stream().filter(curve -> !isDefault(curve)).toList();
    }

    private static boolean isDefault(CurveDefinition curve) {
        return find(curve.id(), BUILT_INS).filter(builtIn -> sameShape(builtIn, curve)).isPresent();
    }

    private static boolean sameShape(CurveDefinition one, CurveDefinition other) {
        return one.mode() == other.mode() && one.amount() == other.amount() && one.points().equals(other.points());
    }

    public static Curve resolve(@Nullable String id, @Nullable List<CurveDefinition> saved) {
        if (StringUtils.isBlank(id)) {
            return Curve.LINEAR;
        }
        return find(id, saved == null ? List.of() : saved)
                .or(() -> find(id, BUILT_INS))
                .map(Curves::of)
                .orElse(Curve.LINEAR);
    }

    public static Curve of(CurveDefinition definition) {
        return definition.mode() == CurveMode.points
                ? new PointsCurve(definition.points())
                : new AmountCurve(definition.amount());
    }

    private static Optional<CurveDefinition> find(String id, List<CurveDefinition> curves) {
        return curves.stream().filter(curve -> id.equals(curve.id())).findFirst();
    }
}
