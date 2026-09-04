package com.getpcpanel.util.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What a duplicate launch tells the already-running instance, and what that instance does about it.
 * Only a launch the user performed raises the UI; a launch the OS performed at logon leaves it closed,
 * so a machine that starts PCPanel twice at sign-in does not open the browser behind the user's back.
 *
 * <p>Exercises the pure overloads so no process state or real data directory is touched.
 */
@DisplayName("FileChecker duplicate-launch signalling")
class FileCheckerDuplicateLaunchTest {
    @TempDir
    Path root;

    @Nested
    @DisplayName("the duplicate records what kind of launch it was")
    class Signalling {
        @Test
        @DisplayName("a user launch")
        void userLaunch() throws IOException {
            var reopen = reopenFile();

            FileChecker.signalRunningInstance(true, reopen);

            assertTrue(reopen.exists());
            assertEquals("user", Files.readString(reopen.toPath()));
        }

        @Test
        @DisplayName("an autostart launch")
        void autostartLaunch() throws IOException {
            var reopen = reopenFile();

            FileChecker.signalRunningInstance(false, reopen);

            assertTrue(reopen.exists());
            assertEquals("autostart", Files.readString(reopen.toPath()));
        }

        @Test
        @DisplayName("the marker is complete the moment it appears, so the watcher cannot read a half-written one")
        void markerIsMovedIntoPlaceComplete() throws IOException {
            var reopen = reopenFile();

            FileChecker.signalRunningInstance(false, reopen);

            assertFalse(new File(root.toFile(), "reopen.txt.tmp").exists(), "the staged file should have been moved, not left behind");
            assertFalse(FileChecker.shouldShowUi(Files.readString(reopen.toPath())));
        }

        @Test
        @DisplayName("a leftover marker from an earlier launch is replaced")
        void leftoverMarkerIsReplaced() throws IOException {
            var reopen = reopenFile();
            Files.writeString(reopen.toPath(), "user");

            FileChecker.signalRunningInstance(false, reopen);

            assertEquals("autostart", Files.readString(reopen.toPath()));
        }
    }

    @Nested
    @DisplayName("the running instance decides from the marker")
    class Deciding {
        @Test
        @DisplayName("a user launch raises the UI")
        void userLaunchShowsUi() {
            assertTrue(FileChecker.shouldShowUi("user"));
        }

        @Test
        @DisplayName("an autostart launch leaves the UI closed")
        void autostartLaunchStaysQuiet() {
            assertFalse(FileChecker.shouldShowUi("autostart"));
            assertFalse(FileChecker.shouldShowUi("autostart\n"));
        }

        @Test
        @DisplayName("an unreadable marker raises the UI rather than swallowing a real gesture")
        void unknownMarkerShowsUi() {
            assertTrue(FileChecker.shouldShowUi(""));
            assertTrue(FileChecker.shouldShowUi(null));
            assertTrue(FileChecker.shouldShowUi("something else"));
        }
    }

    private File reopenFile() {
        return root.resolve("reopen.txt").toFile();
    }
}
