package com.getpcpanel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How a launch is classified from its arguments. The OS autostart entries the installer sets up (the
 * {@code HKCU\Run} value and the administrator scheduled task) both pass {@code quiet}; anything the
 * user starts themselves — a shortcut, the Start menu, the installer's "Launch now" — does not.
 */
@DisplayName("Main launch-argument classification")
class MainLaunchArgsTest {
    @Test
    @DisplayName("the autostart entries are recognised")
    void quietIsAutostart() {
        assertTrue(Main.isAutostartLaunch(Set.of("quiet")));
        assertTrue(Main.isAutostartLaunch(Set.of("quiet", "console")));
    }

    @Test
    @DisplayName("a launch the user performed is not autostart")
    void userLaunchesAreNotAutostart() {
        assertFalse(Main.isAutostartLaunch(Set.of()));
        assertFalse(Main.isAutostartLaunch(Set.of("/postinstall")));
        assertFalse(Main.isAutostartLaunch(Set.of("/updated")));
        assertFalse(Main.isAutostartLaunch(Set.of("skipfilecheck")));
    }
}
