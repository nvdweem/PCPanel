package com.getpcpanel.profile.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.beans.Introspector;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The REST layer edits the stored setting in place rather than replacing it, so it needs one copy that
 * covers every property. The reflective case is the point: a property added later is copied — or this
 * fails — instead of being silently dropped on the way in.
 */
class KnobSettingCopyTest {
    /** Derived from {@link KnobSetting#getCurve()}, so copying the curve already carries it. */
    private static final List<String> DERIVED = List.of("logarithmic", "class");

    @Test
    void copiesEveryProperty() throws Exception {
        var source = new KnobSetting().setCurve("curve-mine");
        source.setMinTrim(12);
        source.setMaxTrim(88);
        source.setOverlayIcon("icon.png");
        source.setButtonDebounce(120);

        var target = new KnobSetting();
        target.copyFrom(source);

        for (var property : Introspector.getBeanInfo(KnobSetting.class).getPropertyDescriptors()) {
            if (DERIVED.contains(property.getName()) || property.getReadMethod() == null) {
                continue;
            }
            assertEquals(property.getReadMethod().invoke(source), property.getReadMethod().invoke(target),
                    "property '" + property.getName() + "' is not copied");
        }
    }

    @Test
    void clearingTheCurveSticks() {
        var target = new KnobSetting().setCurve("curve-mine");

        target.copyFrom(new KnobSetting());

        assertNull(target.getCurve());
    }

    /** A client that only knows the old flag still lands on the logarithmic curve. */
    @Test
    void aLegacyFlagStillSelectsTheLogarithmicCurve() {
        var legacy = new KnobSetting();
        legacy.setLogarithmic(true);

        var target = new KnobSetting();
        target.copyFrom(legacy);

        assertEquals("logarithmic", target.getCurve());
    }
}
