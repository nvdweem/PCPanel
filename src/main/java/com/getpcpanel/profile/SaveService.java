package com.getpcpanel.profile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.Json;
import com.getpcpanel.device.DescriptorFactory;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.util.Debouncer;
import com.getpcpanel.util.FileUtil;
import com.getpcpanel.util.tray.win.WinUser32Ext;
import com.sun.jna.WString;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@ApplicationScoped
public class SaveService {
    private static final String saveFileName = "profiles.json";
    @Inject Event<Object> eventBus;
    @Inject FileUtil fileUtil;
    @Inject Json json;
    @Inject Debouncer debouncer;
    @Inject DeviceHolder devices;
    @SuppressWarnings("StaticNonFinalField") private static String oldVersionEncountered;

    private Save save;
    private boolean isNew = false;
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
            isNew = true;
            return;
        }

        try {
            save = json.read(FileUtils.readFileToString(saveFile, Charset.defaultCharset()), Save.class);
            if (migrateProviderIds(save)) {
                // Legacy DeviceSave entries had no provider identity. Treat this as an old-version
                // read so the existing backup(.bak) + one-time rewrite path persists the migration.
                encounterOldVersion("2.0");
            }
            handleOldVersionEncountered();
            StreamEx.ofValues(save.getDevices()).forEach(d -> StreamEx.of(d.getProfiles()).findFirst(p -> p.isMainProfile()).ifPresent(p -> d.setCurrentProfile(p.getName())));
        } catch (Exception e) {
            log.error("Unable to read file", e);
            loadFailed = true; // Prevent save-on-exit from overwriting a file we could not read
            save = new Save();
            isNew = true;
        }
    }

    /**
     * Fire the initial SaveEvent after all beans are fully initialized.
     * Using @Priority(1) to ensure this runs before DeviceProviderRegistry.onStart() (default priority),
     * which starts the device providers and creates device saves on connect.
     */
    @Priority(1)
    public void onStart(@Observes StartupEvent ev) {
        eventBus.fire(new SaveEvent(save, isNew));
    }

    /**
     * Back-fills {@code providerId} for legacy {@link DeviceSave} entries that predate the
     * self-identifying-device persistence (Phase 2). Such entries can only ever have been PCPanel
     * devices, so a null {@code providerId} becomes {@code "pcpanel"}. {@code deviceKindId} and
     * {@code capabilities} were never stored and cannot be guessed here — they are back-filled from
     * the live descriptor at connect time. Pure (no I/O); returns {@code true} if anything changed
     * so the caller can trigger the version-bump rewrite.
     */
    static boolean migrateProviderIds(Save save) {
        var migrated = false;
        for (var deviceSave : save.getDevices().values()) {
            if (deviceSave.getProviderId() == null) {
                deviceSave.setProviderId(DescriptorFactory.PROVIDER_ID);
                migrated = true;
            }
        }
        return migrated;
    }

    private void handleOldVersionEncountered() {
        if (StringUtils.isBlank(oldVersionEncountered)) {
            return;
        }
        backup();
        writeToFile(); // write file only, SaveEvent will be fired from onStart()
    }

    private synchronized void writeToFile() { // Synchronized: a pending debounced save may run concurrently with the shutdown write
        var saveFile = fileUtil.getFile(saveFileName);
        try {
            FileUtils.writeStringToFile(saveFile, json.writePretty(save), Charset.defaultCharset());
            loadFailed = false; // The file now holds the in-memory state, so save-on-exit can no longer destroy anything
        } catch (IOException e) {
            log.error("Unable to save file", e);
        }
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
        if (localAppData == null) { // Not Windows: nothing to migrate from
            return;
        }
        var oldFile = new File(localAppData, "PCPanel Software/save.json");
        if (oldFile.exists() && confirmMigrate()) {
            try {
                Files.copy(oldFile.toPath(), saveFile.toPath());
            } catch (IOException e) {
                log.error("Unable to copy old save file", e);
            }
            log.info("Migrated old save file to new one");
        }
    }

    /**
     * Asks the user (Windows only) whether to migrate the original PCPanel software's save file.
     *
     * <p>Uses a native Win32 message box via JNA rather than {@code JOptionPane}. Swing's
     * {@code JOptionPane} forces the AWT windowing toolkit ({@code headless=false}), which is
     * unsupported in the GraalVM native image on Windows and breaks {@code libawt} loading for the
     * whole process (headless Java2D, icons, the overlay). The message box keeps the exact yes/no UX
     * without that cost. {@code LOCALAPPDATA} only exists on Windows, so this path is Windows-only.
     */
    private static boolean confirmMigrate() {
        var result = WinUser32Ext.INSTANCE.MessageBoxW(null,
                new WString("No save file found, would you like to migrate from original PCPanel software?"),
                new WString("Migrate"), WinUser32Ext.MB_YESNO | WinUser32Ext.MB_ICONQUESTION);
        return result == WinUser32Ext.IDYES;
    }

    public void save() {
        writeToFile();
        eventBus.fire(new SaveEvent(save, false));
    }

    public void debouncedSave() {
        debouncer.debounce(this, this::save, 1, TimeUnit.SECONDS);
    }

    /**
     * Persist the in-memory state when the app shuts down, so a profile change made within the
     * debounce window (the {@link Debouncer} discards pending tasks on shutdown) is not lost.
     * Writes the file directly without firing a SaveEvent: listeners (OBS/MQTT/OSC/...) must not
     * run while beans are being destroyed.
     */
    public void saveOnExit(@Observes ShutdownEvent ev) {
        if (save == null || loadFailed) {
            return;
        }
        writeToFile();
    }

    public Optional<Profile> getProfile(String serialNum) {
        return devices.getDevice(serialNum).map(device -> get().getDeviceSave(serialNum).ensureCurrentProfile(device.deviceType()));
    }

    public record SaveEvent(Save save, boolean isNew) {
    }
}
