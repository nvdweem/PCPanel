package com.getpcpanel.integration.display;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

/**
 * Decides what a press of a DDC/CI display toggle does: the chosen monitors go off together while any of them is on,
 * else they all come back on. A monitor this toggle switched off counts as off until it switches it on again; otherwise
 * its state is the power mode it reports, and when that says nothing (some monitors answer 0 whatever their state), the
 * state this toggle last set stands in. A monitor that never answered and was never switched is left alone.
 */
public final class DdcToggle {
    private final Map<String, Boolean> lastSetOn = new ConcurrentHashMap<>();

    /**
     * @param turnOff whether the monitors go off (else on)
     * @param ids     the monitors to switch; the ones already in that state are left out
     */
    public record Plan(boolean turnOff, List<String> ids) {
    }

    /** {@code powerModes}: the power mode each monitor reported, by id, in order; null when it did not answer. */
    public Plan plan(Map<String, Integer> powerModes) {
        var states = new LinkedHashMap<String, Boolean>();
        powerModes.forEach((id, mode) -> {
            var state = state(id, mode);
            if (state != null) {
                states.put(id, state);
            }
        });
        var turnOff = states.containsValue(true);
        var ids = new ArrayList<String>();
        states.forEach((id, on) -> {
            if (on == turnOff) {
                ids.add(id);
            }
        });
        return new Plan(turnOff, ids);
    }

    /** Records that monitor {@code id} was switched on ({@code on}) or off. */
    public void switched(String id, boolean on) {
        lastSetOn.put(id, on);
    }

    private @Nullable Boolean state(String id, @Nullable Integer mode) {
        var last = lastSetOn.get(id);
        if (Boolean.FALSE.equals(last)) {
            // Switched off by this toggle: off until it switches it on, whatever it reports. Some monitors report on
            // while off (an MSI OLED does), and counting it as on would keep switching it "off" while the rest stay dark.
            return false;
        }
        if (mode == null) {
            return null;
        }
        var reported = DdcOffCode.isOn(mode);
        if (reported != null) {
            return reported;
        }
        return last == null || last;
    }
}
