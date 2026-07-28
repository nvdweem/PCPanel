package com.getpcpanel.rest.model.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.getpcpanel.commands.curve.AmountCurve;
import com.getpcpanel.commands.curve.Curves;
import com.getpcpanel.profile.Save;
import com.getpcpanel.profile.dto.CurveDefinition;
import com.getpcpanel.profile.dto.CurveMode;

/**
 * The picker needs to see the built-ins, but storing them back would freeze today's defaults into the
 * save file and turn "reset to default" into a no-op. So the DTO carries the whole library outwards and
 * only the user's own work inwards.
 */
class SettingsDtoCurvesTest {
    private static CurveDefinition amount(String id, String name, int amount) {
        return new CurveDefinition(id, name, CurveMode.amount, amount, List.of());
    }

    private static Save saveWith(List<CurveDefinition> curves) {
        var save = new Save();
        save.setCurves(curves);
        return save;
    }

    private static List<CurveDefinition> roundTrip(List<CurveDefinition> curves) {
        var dto = SettingsDto.from(saveWith(curves));
        var target = new Save();
        dto.applyTo(target);
        return target.getCurves();
    }

    @Test
    void theBuiltInsAreOfferedEvenWhenNothingIsSaved() {
        var ids = SettingsDto.from(new Save()).getCurves().stream().map(CurveDefinition::id).toList();
        assertEquals(List.of(Curves.LINEAR_ID, Curves.LOGARITHMIC_ID), ids);
    }

    @Test
    void theUsersOwnCurvesFollowTheBuiltIns() {
        var ids = SettingsDto.from(saveWith(List.of(amount("mine", "Mine", 20))))
                             .getCurves().stream().map(CurveDefinition::id).toList();
        assertEquals(List.of(Curves.LINEAR_ID, Curves.LOGARITHMIC_ID, "mine"), ids);
    }

    @Test
    void untouchedBuiltInsAreNotWrittenToTheSaveFile() {
        assertTrue(roundTrip(List.of()).isEmpty());
    }

    @Test
    void aRetunedBuiltInIsWrittenToTheSaveFile() {
        var retuned = amount(Curves.LOGARITHMIC_ID, "Logarithmic", 20);
        assertEquals(List.of(retuned), roundTrip(List.of(retuned)));
    }

    @Test
    void aRetunedBuiltInPutBackToItsDefaultStopsBeingStored() {
        var asDefault = amount(Curves.LOGARITHMIC_ID, "Logarithmic", AmountCurve.LOG_AMOUNT);
        assertTrue(roundTrip(List.of(asDefault)).isEmpty(), "an entry equal to the default should not be stored");
    }

    @Test
    void userCurvesSurviveTheRoundTrip() {
        var mine = amount("mine", "Mine", 20);
        assertEquals(List.of(mine), roundTrip(List.of(mine)));
    }
}
