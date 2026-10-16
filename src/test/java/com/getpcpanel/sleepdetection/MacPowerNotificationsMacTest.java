package com.getpcpanel.sleepdetection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Registers for the real IOKit power notifications and shuts down again; enable with {@code -Dpcpanel.mactest=true}. */
@EnabledOnOs(OS.MAC)
@EnabledIfSystemProperty(named = "pcpanel.mactest", matches = "true")
class MacPowerNotificationsMacTest {
    @Test
    void registersRunsAndShutsDownCleanly() {
        var notifications = new MacPowerNotifications(type -> { }, () -> { });
        assertTrue(notifications.start(), "registration with IOKit failed");
        assertTrue(notifications.isRunning());
        notifications.close();
        assertFalse(notifications.isRunning(), "run loop thread did not stop");
    }
}
