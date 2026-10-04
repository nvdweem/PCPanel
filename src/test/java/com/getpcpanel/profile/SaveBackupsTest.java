package com.getpcpanel.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SaveBackupsTest {
    @TempDir Path tmp;
    private File saveFile;
    private MutableClock clock;
    private SaveBackups backups;

    @BeforeEach
    void setUp() throws IOException {
        saveFile = tmp.resolve("profiles.json").toFile();
        Files.writeString(saveFile.toPath(), "{\"v\":1}");
        clock = new MutableClock(Instant.parse("2026-10-02T10:00:00Z"));
        backups = new SaveBackups(tmp.resolve("backups").toFile(), clock);
    }

    @Test
    void snapshotsAtMostOncePerInterval() {
        backups.snapshotIfDue(saveFile);
        clock.advance(Duration.ofMinutes(5));
        backups.snapshotIfDue(saveFile);
        assertEquals(1, backups.list().size());

        clock.advance(Duration.ofMinutes(6));
        backups.snapshotIfDue(saveFile);
        assertEquals(2, backups.list().size());
    }

    @Test
    void keepsOnlyTheNewest() {
        for (var i = 0; i < SaveBackups.KEEP + 3; i++) {
            backups.snapshot(saveFile);
            clock.advance(Duration.ofMinutes(11));
        }
        var list = backups.list();
        assertEquals(SaveBackups.KEEP, list.size());
        assertTrue(list.get(0).timestamp() > list.get(list.size() - 1).timestamp(), "newest first");
    }

    @Test
    void findOnlyResolvesOwnSnapshots() throws IOException {
        backups.snapshot(saveFile);
        var name = backups.list().get(0).name();
        assertTrue(backups.find(name).isPresent());
        assertTrue(backups.find("../profiles.json").isEmpty());
        Files.writeString(tmp.resolve("backups/other.json"), "{}");
        assertTrue(backups.find("other.json").isEmpty());
    }

    @Test
    void noSaveFileNoSnapshot() {
        backups.snapshot(tmp.resolve("missing.json").toFile());
        assertTrue(backups.list().isEmpty());
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.systemDefault();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
