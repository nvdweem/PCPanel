package com.getpcpanel.util.io;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The single-instance decision on the lock file, as {@link FileChecker#heldByAnotherProcess} makes it. */
class FileCheckerLockTest {
    @TempDir
    Path dir;

    @Test
    void aFreeLockIsTakenAndIsNoDuplicate() throws IOException {
        try (var file = new RandomAccessFile(dir.resolve("lock.txt").toFile(), "rw")) {
            assertFalse(FileChecker.heldByAnotherProcess(file.getChannel()));
            assertTrue(file.getChannel().isOpen());
        }
    }

    /**
     * A dev-mode live reload runs {@code Main.main()} again in the same JVM, in a new class loader, while the
     * previous start still holds the lock. Java refuses a second lock on the file within one process; that must
     * count as "not a duplicate", not end the restart.
     */
    @Test
    void aLockAlreadyHeldInThisProcessIsNoDuplicate() throws IOException {
        var lock = dir.resolve("lock.txt").toFile();
        try (var first = new RandomAccessFile(lock, "rw"); var second = new RandomAccessFile(lock, "rw")) {
            assertFalse(FileChecker.heldByAnotherProcess(first.getChannel()), "the first start takes the lock");
            assertFalse(FileChecker.heldByAnotherProcess(second.getChannel()), "the reloaded start in the same process");
        }
    }
}
