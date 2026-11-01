package com.getpcpanel.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.getpcpanel.AppLikeMapper;
import com.getpcpanel.Json;
import com.getpcpanel.device.DeviceHolder;
import com.getpcpanel.device.provider.pcpanel.DeviceType;
import com.getpcpanel.template.TemplateSaveMigration;
import com.getpcpanel.util.io.FileUtil;

/** A save file that cannot be read is kept aside, so the empty configuration written after it cannot destroy it. */
class SaveServiceUnreadableFileTest {
    private static final String UNREADABLE = "{ \"devices\": { this is not json";

    @TempDir Path tmp;

    @Test
    void anUnreadableFileIsKeptBeforeAnythingOverwritesIt() throws Exception {
        Files.writeString(tmp.resolve("profiles.json"), UNREADABLE);
        var sut = saveService();
        sut.load();

        sut.get().createSaveForNewDevice("dev", DeviceType.PCPANEL_PRO);
        sut.save();

        try (var files = Files.list(tmp)) {
            var kept = files.filter(f -> f.getFileName().toString().matches("profiles\\.json\\.unreadable-\\d{8}-\\d{6}\\.bak")).toList();
            assertEquals(1, kept.size(), "one copy of the unreadable file");
            assertEquals(UNREADABLE, Files.readString(kept.getFirst()));
        }
        assertTrue(Files.readString(tmp.resolve("profiles.json")).contains("\"dev\""), "the new configuration is written");
    }

    private SaveService saveService() throws Exception {
        var fileUtil = new FileUtil();
        var root = FileUtil.class.getDeclaredField("rootPath");
        root.setAccessible(true);
        root.set(fileUtil, tmp.toString());
        var ensure = FileUtil.class.getDeclaredMethod("ensureRoot");
        ensure.setAccessible(true);
        ensure.invoke(fileUtil);

        var json = new Json();
        var mapper = Json.class.getDeclaredField("mapper");
        mapper.setAccessible(true);
        mapper.set(json, AppLikeMapper.build());

        var sut = new SaveService();
        sut.fileUtil = fileUtil;
        sut.json = json;
        sut.devices = new DeviceHolder();
        sut.templateMigration = new TemplateSaveMigration();
        sut.eventBus = new SaveServiceHistoryTest.NoEvents();
        sut.debouncer = new com.getpcpanel.util.concurrent.Debouncer();
        return sut;
    }
}
