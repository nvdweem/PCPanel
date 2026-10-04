package com.getpcpanel.integration.display;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class DdcToggleTest {
    private static Map<String, Integer> modes(Object... idsAndModes) {
        var result = new LinkedHashMap<String, Integer>();
        for (var i = 0; i < idsAndModes.length; i += 2) {
            result.put((String) idsAndModes[i], (Integer) idsAndModes[i + 1]);
        }
        return result;
    }

    @Test
    void anyOnTurnsTheOnesThatAreOnOff() {
        assertEquals(new DdcToggle.Plan(true, List.of("a")), new DdcToggle().plan(modes("a", 1, "b", 4)));
    }

    @Test
    void allOffTurnsThemAllOn() {
        assertEquals(new DdcToggle.Plan(false, List.of("a", "b")), new DdcToggle().plan(modes("a", 4, "b", 5)));
    }

    @Test
    void aMonitorThatReportsOnAfterBeingSwitchedOffCountsAsOff() {
        // An MSI OLED reports power mode 1 while it is off; a Dell next to it reports 4.
        var toggle = new DdcToggle();
        var off = toggle.plan(modes("msi", 1, "dell", 1));
        assertEquals(new DdcToggle.Plan(true, List.of("msi", "dell")), off);
        off.ids().forEach(id -> toggle.switched(id, false));

        assertEquals(new DdcToggle.Plan(false, List.of("msi", "dell")), toggle.plan(modes("msi", 1, "dell", 4)), "the next press turns both on");

        toggle.switched("msi", true);
        toggle.switched("dell", true);
        assertEquals(new DdcToggle.Plan(true, List.of("msi", "dell")), toggle.plan(modes("msi", 1, "dell", 1)), "once on, the reported mode counts again");
    }

    @Test
    void aMonitorThatStopsAnsweringAfterBeingSwitchedOffCountsAsOff() {
        var toggle = new DdcToggle();
        toggle.switched("a", false);
        assertEquals(new DdcToggle.Plan(false, List.of("a")), toggle.plan(modes("a", null)));
    }

    @Test
    void aMeaninglessModeFallsBackToTheLastSetState() {
        var toggle = new DdcToggle();
        assertEquals(new DdcToggle.Plan(true, List.of("a")), toggle.plan(modes("a", 0)), "never switched: assume on");
        toggle.switched("a", true);
        assertEquals(new DdcToggle.Plan(true, List.of("a")), toggle.plan(modes("a", 255)));
    }

    @Test
    void aMonitorThatNeverAnsweredIsLeftAlone() {
        assertEquals(new DdcToggle.Plan(false, List.of()), new DdcToggle().plan(modes("a", null)));
    }
}
