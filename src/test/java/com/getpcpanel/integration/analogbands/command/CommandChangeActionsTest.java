package com.getpcpanel.integration.analogbands.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CommandChangeActionsTest {
    @Test
    void throttleDefaultsAndIsClamped() {
        assertEquals(CommandChangeActions.DEFAULT_THROTTLE_MS, new CommandChangeActions(null, null).getThrottleMs());
        assertEquals(0, new CommandChangeActions(null, 0).getThrottleMs());
        assertEquals(0, new CommandChangeActions(null, -5).getThrottleMs());
        assertEquals(CommandChangeActions.MAX_THROTTLE_MS, new CommandChangeActions(null, 60_000).getThrottleMs());
    }
}
