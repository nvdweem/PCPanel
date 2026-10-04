package com.getpcpanel.integration.program;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.program.IPlatformCommand.LinuxPlatformCommand;

class LinuxPlatformCommandTest {
    @Test
    void desktopEntriesAreLaunchedAsApps() {
        assertTrue(LinuxPlatformCommand.isDesktopEntry("/usr/share/applications/firefox.desktop"));
        assertFalse(LinuxPlatformCommand.isDesktopEntry("firefox.desktop --flag"));
        assertFalse(LinuxPlatformCommand.isDesktopEntry("/usr/bin/firefox"));
        assertFalse(LinuxPlatformCommand.isDesktopEntry("https://example.com/app.desktop/"));
    }

    @Test
    void gioLaunchesTheEntryAndGtkLaunchByIdOtherwise() {
        assertArrayEquals(new String[] { "sh", "-c", "gio launch \"$1\" 2>/dev/null || gtk-launch \"$(basename \"$1\" .desktop)\"", "sh",
                        "/usr/share/applications/firefox.desktop" },
                LinuxPlatformCommand.desktopEntryLaunch("/usr/share/applications/firefox.desktop"));
    }
}
