package com.getpcpanel.profile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.getpcpanel.profile.dto.SaveBackup;

import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

/**
 * Rolling snapshots of the save file in {@code ${pcpanel.root}/backups}. A snapshot is the file as it was
 * before a write, taken at most once per {@link #MIN_INTERVAL}, and only the newest {@link #KEEP} are kept.
 * Names carry their own timestamp ({@code profiles-20261002-143005.json}), so the directory is the whole state.
 */
@Log4j2
public class SaveBackups {
    static final Duration MIN_INTERVAL = Duration.ofMinutes(10);
    static final int KEEP = 10;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Pattern NAME = Pattern.compile("profiles-(\\d{8}-\\d{6})\\.json");

    private final File dir;
    private final Clock clock;

    public SaveBackups(File dir, Clock clock) {
        this.dir = dir;
        this.clock = clock;
    }

    /** Snapshots {@code saveFile} unless the newest snapshot is younger than {@link #MIN_INTERVAL}. */
    public void snapshotIfDue(File saveFile) {
        var newest = list().stream().findFirst();
        if (newest.isPresent() && clock.millis() - newest.get().timestamp() < MIN_INTERVAL.toMillis()) {
            return;
        }
        snapshot(saveFile);
    }

    /** Snapshots {@code saveFile} now, regardless of the interval. */
    public void snapshot(File saveFile) {
        if (!saveFile.isFile()) {
            return;
        }
        if (!dir.isDirectory() && !dir.mkdirs()) {
            log.warn("Unable to create backup directory {}", dir);
            return;
        }
        var name = "profiles-" + STAMP.format(LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault())) + ".json";
        try {
            Files.copy(saveFile.toPath(), new File(dir, name).toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warn("Unable to back up {}", saveFile, e);
            return;
        }
        prune();
    }

    /** Snapshots, newest first. */
    public List<SaveBackup> list() {
        var files = dir.listFiles();
        if (files == null) {
            return List.of();
        }
        return StreamEx.of(files)
                       .map(SaveBackups::toSaveBackup)
                       .nonNull()
                       .reverseSorted(Comparator.comparingLong(SaveBackup::timestamp).thenComparing(SaveBackup::name))
                       .toList();
    }

    /** The snapshot file called {@code name}, if it is one of ours — never an arbitrary path. */
    public Optional<File> find(String name) {
        return StreamEx.of(list()).findFirst(b -> b.name().equals(name)).map(b -> new File(dir, b.name()));
    }

    private void prune() {
        StreamEx.of(list()).skip(KEEP).forEach(b -> {
            var file = new File(dir, b.name());
            if (!file.delete()) {
                log.warn("Unable to delete old backup {}", file);
            }
        });
    }

    @Nullable
    private static SaveBackup toSaveBackup(File file) {
        var m = NAME.matcher(file.getName());
        if (!file.isFile() || !m.matches()) {
            return null;
        }
        var time = LocalDateTime.parse(m.group(1), STAMP).atZone(ZoneId.systemDefault()).toInstant();
        return new SaveBackup(file.getName(), time.toEpochMilli(), file.length());
    }
}
