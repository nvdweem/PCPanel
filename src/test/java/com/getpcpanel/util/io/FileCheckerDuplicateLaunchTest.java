package com.getpcpanel.util.io;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What a duplicate launch asks the already-running instance to do. Only a launch the user performed
 * themselves raises the UI; an autostart launch leaves it closed, so a machine that starts PCPanel
 * twice at logon does not open the browser behind the user's back.
 *
 * <p>Exercises the pure {@code signalRunningInstance(userLaunch, reopenFile)} overload so no process
 * state or real data directory is touched.
 */
@DisplayName("FileChecker duplicate-launch signalling")
class FileCheckerDuplicateLaunchTest {
    @TempDir
    Path root;

    @Test
    @DisplayName("a user launch asks the running instance to show the UI")
    void userLaunchRequestsShow() throws IOException {
        var reopen = reopenFile();

        FileChecker.signalRunningInstance(true, reopen);

        assertTrue(reopen.exists(), "a user launch should leave the reopen marker for the running instance");
    }

    @Test
    @DisplayName("an autostart launch leaves the UI closed")
    void autostartLaunchStaysQuiet() throws IOException {
        var reopen = reopenFile();

        FileChecker.signalRunningInstance(false, reopen);

        assertFalse(reopen.exists(), "an autostart launch should not ask the running instance to open the browser");
    }

    @Test
    @DisplayName("a leftover marker does not fail a user launch")
    void leftoverMarkerIsTolerated() throws IOException {
        var reopen = reopenFile();
        assertTrue(reopen.createNewFile());

        FileChecker.signalRunningInstance(true, reopen);

        assertTrue(reopen.exists());
    }

    private File reopenFile() {
        return root.resolve("reopen.txt").toFile();
    }
}
