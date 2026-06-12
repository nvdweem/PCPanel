package com.getpcpanel.profile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.swing.JOptionPane;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.getpcpanel.Json;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.util.Debouncer;
import com.getpcpanel.util.FileUtil;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Service
@RequiredArgsConstructor
public class SaveService {
    private static final String saveFileName = "profiles.json";
    private final ApplicationEventPublisher eventPublisher;
    private final FileUtil fileUtil;
    private final Json json;
    private final Debouncer debouncer;
    @Autowired @Lazy @Setter private DeviceHolder devices;
    @SuppressWarnings("StaticNonFinalField") private static String oldVersionEncountered;

    private Save save;
    private volatile boolean loadFailed;

    public Save get() {
        return save;
    }

    @PostConstruct
    public void load() {
        var saveFile = fileUtil.getFile(saveFileName);
        if (!saveFile.exists()) {
            tryMigrate(saveFile);
        }
        if (!saveFile.exists()) {
            log.info("No save file found, creating new one");
            save = new Save();
            eventPublisher.publishEvent(new SaveEvent(save, true));
            return;
        }

        try {
            save = json.read(FileUtils.readFileToString(saveFile, Charset.defaultCharset()), Save.class);
            handleOldVersionEncountered();
            StreamEx.ofValues(save.getDevices()).forEach(d -> StreamEx.of(d.getProfiles()).findFirst(Profile::isMainProfile).ifPresent(p -> d.setCurrentProfile(p.getName())));
            eventPublisher.publishEvent(new SaveEvent(save, false));
        } catch (Exception e) {
            log.error("Unable to read file", e);
            loadFailed = true; // Prevent save-on-exit from overwriting a file we could not read
            save = new Save();
        }
    }

    private void handleOldVersionEncountered() {
        if (StringUtils.isBlank(oldVersionEncountered)) {
            return;
        }
        backup();
        save();
    }

    private void backup() {
        try {
            FileUtils.copyFile(fileUtil.getFile(saveFileName), fileUtil.getFile(saveFileName + "." + oldVersionEncountered + ".bak"));
        } catch (IOException e) {
            log.error("Unable to backup profile", e);
        }
    }

    /**
     * Gets called when an older version of a profile is read.
     */
    public static void encounterOldVersion(String version) {
        oldVersionEncountered = version.replace('.', '_');
    }

    private void tryMigrate(File saveFile) {
        @SuppressWarnings("CallToSystemGetenv") var localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null) {
            return;
        }
        var oldFile = new File(localAppData, "PCPanel Software/save.json");
        if (oldFile.exists()) {
            var result = JOptionPane.showConfirmDialog(null, "No save file found, would you like to migrate from original PCPanel software?", "Migrate", JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                try {
                    Files.copy(oldFile.toPath(), saveFile.toPath());
                } catch (IOException e) {
                    log.error("Unable to copy old save file", e);
                }
                log.info("Migrated old save file to new one");
            }
        }
    }

    public void save() {
        writeSaveFile();
        eventPublisher.publishEvent(new SaveEvent(save, false));
    }

    private synchronized void writeSaveFile() { // Synchronized: a pending debounced save may run concurrently with the shutdown-thread write
        var saveFile = fileUtil.getFile(saveFileName);
        try {
            FileUtils.writeStringToFile(saveFile, json.writePretty(save), Charset.defaultCharset());
            loadFailed = false; // The file now holds the in-memory state, so save-on-exit can no longer destroy anything
        } catch (IOException e) {
            log.error("Unable to save file", e);
        }
    }

    public void debouncedSave() {
        debouncer.debounce(this, this::save, 1, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void saveOnExit() {
        if (save == null || loadFailed) {
            return;
        }
        // Writes the full in-memory state, so any save still pending in the debouncer is covered too.
        // Write directly without publishing SaveEvent: listeners (OBS/MQTT/OSC/...) must not run while beans are being destroyed.
        writeSaveFile();
    }

    public Optional<Profile> getProfile(String serialNum) {
        return devices.getDevice(serialNum).map(device -> get().getDeviceSave(serialNum).ensureCurrentProfile(device.getDeviceType()));
    }

    public record SaveEvent(Save save, boolean isNew) {
    }
}

