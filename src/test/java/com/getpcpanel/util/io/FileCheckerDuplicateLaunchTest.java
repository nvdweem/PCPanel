package com.getpcpanel.util.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

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

    /**
     * The watcher reacts to the marker being created and reads its contents right then, so the whole
     * scheme rests on a move into the watched directory arriving as {@code ENTRY_CREATE} with the
     * contents already complete. Pinned here because it is an OS-behaviour assumption, and if it ever
     * stopped holding the running instance would silently read an empty marker and open the browser on
     * every duplicate launch again.
     */
    @Nested
    @DisplayName("the watcher contract the marker relies on")
    class WatcherContract {
        @Test
        @DisplayName("a moved-in marker arrives as ENTRY_CREATE, complete")
        void moveFiresCreateWithCompleteContents() throws IOException, InterruptedException {
            try (var watcher = FileSystems.getDefault().newWatchService()) {
                root.register(watcher, StandardWatchEventKinds.ENTRY_CREATE);

                FileChecker.signalRunningInstance(false, reopenFile());

                var contents = awaitMarker(watcher);
                assertEquals("autostart", contents, "the watcher should see the finished marker, not an empty file");
            }
        }

        /** Contents of {@code reopen.txt} read the instant its ENTRY_CREATE arrives, as the watcher does. */
        private String awaitMarker(WatchService watcher) throws InterruptedException, IOException {
            var deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (System.nanoTime() < deadline) {
                var key = watcher.poll(1, TimeUnit.SECONDS);
                if (key == null) {
                    continue;
                }
                for (var event : key.pollEvents()) {
                    if ("reopen.txt".equals(event.context().toString())) {
                        return Files.readString(reopenFile().toPath());
                    }
                }
                key.reset();
            }
            return "<no ENTRY_CREATE observed>";
        }
    }

    private File reopenFile() {
        return root.resolve("reopen.txt").toFile();
    }
}
