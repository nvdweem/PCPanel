package com.getpcpanel.profile.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.commands.curve.Curves;

/**
 * Named curves replace the {@code logarithmic} boolean on a control. Every profiles.json written before
 * this feature only carries that boolean, so it stays part of the JSON contract: read to seed the curve,
 * written back so a downgraded app still reads a sensible value out of a new save file.
 */
class KnobSettingCurveCompatTest {
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private KnobSetting read(String json) throws Exception {
        return mapper.readValue(json, KnobSetting.class);
    }

    @Test
    void aLegacyLogarithmicControlResolvesToTheBuiltInLogCurve() throws Exception {
        var setting = read("""
                {"minTrim":0,"maxTrim":100,"logarithmic":true,"buttonDebounce":50}""");
        assertEquals(Curves.LOGARITHMIC_ID, setting.getCurve());
        assertTrue(setting.isLogarithmic());
    }

    @Test
    void aLegacyLinearControlHasNoCurve() throws Exception {
        var setting = read("""
                {"minTrim":0,"maxTrim":100,"logarithmic":false}""");
        assertNull(setting.getCurve());
        assertFalse(setting.isLogarithmic());
    }

    @Test
    void aLegacySaveWithoutTheFlagAtAllIsLinear() throws Exception {
        assertNull(read("{\"minTrim\":10}").getCurve());
    }

    @Test
    void everyOtherLegacySettingSurvivesTheRead() throws Exception {
        var setting = read("""
                {"minTrim":15,"maxTrim":85,"logarithmic":true,"overlayIcon":"icon.png","buttonDebounce":75}""");
        assertEquals(15, setting.getMinTrim());
        assertEquals(85, setting.getMaxTrim());
        assertEquals("icon.png", setting.getOverlayIcon());
        assertEquals(75, setting.getButtonDebounce());
    }

    @Test
    void aNamedCurveIsNotReportedAsLogarithmic() throws Exception {
        var setting = read("""
                {"curve":"my-taper","logarithmic":false}""");
        assertEquals("my-taper", setting.getCurve());
        assertFalse(setting.isLogarithmic());
    }

    @Test
    void theLegacyFlagNeverOverwritesAnExplicitCurveWhicheverOrderTheyAppear() throws Exception {
        assertEquals("my-taper", read("""
                {"curve":"my-taper","logarithmic":true}""").getCurve());
        assertEquals("my-taper", read("""
                {"logarithmic":true,"curve":"my-taper"}""").getCurve());
    }

    @Test
    void anEmptyCurveBesideTheLegacyFlagIsStillLogarithmic() throws Exception {
        // A null curve is no curve, so the flag beside it is the only thing naming one — whichever order
        // the two arrive in. Property order is not ours to choose: a document sorted alphabetically puts
        // curve first, and a reader that let that null settle the question would drop the user's setting.
        assertEquals(Curves.LOGARITHMIC_ID, read("""
                {"curve":null,"logarithmic":true}""").getCurve());
        assertEquals(Curves.LOGARITHMIC_ID, read("""
                {"logarithmic":true,"curve":null}""").getCurve());
        assertNull(read("""
                {"curve":null,"logarithmic":false}""").getCurve());
    }

    @Test
    void noCurveIsWrittenAsAnAbsentProperty() throws Exception {
        // Writing an explicit null would put one in every save file, next to the flag it must not silence.
        assertNull(mapper.readTree(mapper.writeValueAsString(new KnobSetting())).get("curve"));
    }

    @Test
    void aDowngradedAppStillSeesTheLogarithmicFlag() throws Exception {
        var logarithmic = new KnobSetting();
        logarithmic.setCurve(Curves.LOGARITHMIC_ID);
        assertTrue(mapper.readTree(mapper.writeValueAsString(logarithmic)).get("logarithmic").asBoolean());

        var custom = new KnobSetting();
        custom.setCurve("my-taper");
        assertFalse(mapper.readTree(mapper.writeValueAsString(custom)).get("logarithmic").asBoolean());
    }

    @Test
    void writingAndReadingBackKeepsTheCurve() throws Exception {
        var setting = new KnobSetting();
        setting.setCurve("my-taper");
        setting.setMinTrim(20);
        assertEquals("my-taper", read(mapper.writeValueAsString(setting)).getCurve());
        assertEquals(20, read(mapper.writeValueAsString(setting)).getMinTrim());
    }
}
