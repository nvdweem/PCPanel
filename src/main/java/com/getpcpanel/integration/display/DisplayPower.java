package com.getpcpanel.integration.display;

/**
 * Switches the displays off; they come back on with mouse or keyboard input. One build-time implementation per
 * platform, reached from the command layer via {@link com.getpcpanel.util.CdiHelper}. Best-effort: a failure is
 * logged, never thrown.
 */
public interface DisplayPower {
    void turnOff();
}
