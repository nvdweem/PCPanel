package com.getpcpanel.integration.display;

import java.util.List;

/**
 * Switches the displays off; they come back on with mouse or keyboard input. One build-time implementation per
 * platform, reached from the command layer via {@link com.getpcpanel.util.CdiHelper}. Best-effort: a failure is
 * logged, never thrown.
 *
 * <p>Single monitors are switched over their cable with DDC/CI (VCP code {@code D6}, power mode), which leaves the
 * screen layout alone. A laptop's built-in panel does not speak DDC/CI, so only {@link #turnOff()} reaches it.
 */
public interface DisplayPower {
    /** Every display, put to sleep the way that wakes on mouse or keyboard input. */
    void turnOff();

    /** The monitors {@link #toggle} can switch, in the platform's order. */
    default List<DisplayInfo> list() {
        return List.of();
    }

    /**
     * Turns the displays with these {@link DisplayInfo#id ids} off while any of them is on, else back on. Displays
     * that are not connected or do not answer are skipped.
     */
    default void toggle(List<String> ids) {
        turnOff();
    }

    /**
     * @param id stays the same across restarts: the monitor's device path on Windows, its I2C bus number on Linux
     * @param name what the user picks it by, see {@link DisplayNames}
     */
    record DisplayInfo(String id, String name) {
    }
}
