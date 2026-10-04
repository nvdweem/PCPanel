package com.getpcpanel.profile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.getpcpanel.Json;
import com.getpcpanel.device.provider.pcpanel.DescriptorFactory;
import com.getpcpanel.device.provider.pcpanel.DeviceScanner;
import com.getpcpanel.device.Device;
import com.getpcpanel.device.DeviceHolder;
import com.getpcpanel.profile.dto.LightingConfig;
import com.getpcpanel.profile.dto.SaveBackup;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig;
import com.getpcpanel.profile.dto.SingleSliderLabelLightingConfig;
import com.getpcpanel.profile.dto.SingleSliderLightingConfig;
import com.getpcpanel.template.TemplateSaveMigration;
import com.getpcpanel.util.concurrent.Debouncer;
import com.getpcpanel.util.io.FileUtil;
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
    /** Undo steps kept. */
    private static final int HISTORY = 30;
    @Inject Event<Object> eventBus;
    @Inject FileUtil fileUtil;
    @Inject Json json;
    @Inject Debouncer debouncer;
    @Inject DeviceHolder devices;
    @Inject TemplateSaveMigration templateMigration;
    @SuppressWarnings("StaticNonFinalField") private static String oldVersionEncountered;

    private Save save;
    private SaveBackups backups;
    private final Deque<String> undo = new ArrayDeque<>();
    private final Deque<String> redo = new ArrayDeque<>();
    /**
     * Whether changes still join the current undo step. The UI closes it with {@link #checkpoint()} when the user moves
     * away from what they were editing (another action, another page, a saved settings form), so editing five fields of
     * one action is one step.
     */
    private boolean groupOpen;
    /** A change is waiting for its debounced write (see {@link #debouncedSave()}). */
    private volatile boolean unwritten;
    /** What the last undo or redo changed, in a few words for the UI. */
    private List<String> lastChange = List.of();
    private boolean isNew = false;
    private volatile boolean loadFailed;

    public Save get() {
        return save;
    }

    /** True when this run started from a blank {@link Save} — no save file existed (a first run) or the
     *  existing one could not be read. Drives the first-run onboarding (open browser + welcome dialog). */
    public boolean isNewSave() {
        return isNew;
    }

    @PostConstruct
    public void load() {
        backups = new SaveBackups(fileUtil.getFile("backups"), Clock.systemDefaultZone());
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
            save = read(saveFile);
            handleOldVersionEncountered();
            StreamEx.ofValues(save.getDevices()).forEach(d -> StreamEx.of(d.getProfiles()).findFirst(p -> p.isMainProfile()).ifPresent(p -> d.setCurrentProfile(p.getName())));
        } catch (Exception e) {
            log.error("Unable to read file", e);
            loadFailed = true; // Prevent save-on-exit from overwriting a file we could not read
            save = new Save();
            isNew = true;
        }
    }

    /** Reads and migrates a save file; flags an old-version read for {@link #handleOldVersionEncountered()}. */
    private Save read(File file) throws IOException {
        return read(FileUtils.readFileToString(file, Charset.defaultCharset()));
    }

    private Save read(String text) {
        var document = json.readTree(text);
        var migratedTemplates = templateMigration.migrate(document);
        var read = json.read(document, Save.class);
        var migratedProviderIds = migrateProviderIds(read);
        var migratedMuteTargets = migrateMuteOverrideFollow(read);
        if (migratedProviderIds || migratedMuteTargets) {
            // Both rewrite values an older file spells differently. Treat either as an old-version
            // read so the existing backup(.bak) + one-time rewrite path persists the migration.
            encounterOldVersion("2.0");
        } else if (migratedTemplates) {
            encounterOldVersion("2.1");
        }
        return read;
    }

    public List<SaveBackup> backups() {
        return backups.list();
    }

    /**
     * Replaces the configuration with the snapshot called {@code name}. The current file is snapshotted first,
     * so a restore can itself be undone. Connected devices are re-announced so each is rebuilt on its restored
     * {@link DeviceSave} (a {@link Device} holds its own reference) and relit.
     */
    public synchronized boolean restore(String name) throws IOException {
        var file = backups.find(name);
        if (file.isEmpty()) {
            return false;
        }
        var restored = read(file.get());
        backups.snapshot(fileUtil.getFile(saveFileName));
        push(undo, json.writePretty(save));
        redo.clear();
        apply(restored);
        log.info("Restored configuration from backup {}", name);
        return true;
    }

    /** The user moved away from what they were editing: the next change starts a new undo step. */
    public void checkpoint() {
        synchronized (this) {
            writePending(); // the debounced write of the edit just finished still belongs to its step
            groupOpen = false;
        }
        announceHistory();
    }

    /** A change still waiting for its write can be undone too: {@link #undo()} writes it first. */
    public synchronized boolean canUndo() {
        return !undo.isEmpty() || hasUnwrittenEdit();
    }

    /** An edit clears what there was to redo, also before it is written. */
    public synchronized boolean canRedo() {
        return !redo.isEmpty() && !hasUnwrittenEdit();
    }

    /** What the last {@link #undo()} or {@link #redo()} changed, such as {@code K3 actions · Gaming}. */
    public synchronized List<String> lastChange() {
        return lastChange;
    }

    /** Goes back to the configuration before the last change. */
    public boolean undo() {
        boolean done;
        synchronized (this) {
            done = step(undo, redo);
        }
        announceHistory();
        return done;
    }

    /** Re-applies the change {@link #undo()} took back. */
    public boolean redo() {
        boolean done;
        synchronized (this) {
            done = step(redo, undo);
        }
        announceHistory();
        return done;
    }

    /** Tells the UI what can be undone or redone now. */
    private void announceHistory() {
        eventBus.fire(new HistoryChangedEvent(canUndo(), canRedo()));
    }

    /** The in-memory configuration holds an edit (not just another active profile) that is not in the file yet. */
    private boolean hasUnwrittenEdit() {
        var saveFile = fileUtil.getFile(saveFileName);
        if (!unwritten || save == null || !saveFile.isFile()) {
            return false;
        }
        try {
            var written = FileUtils.readFileToString(saveFile, Charset.defaultCharset());
            return !withoutCurrentProfiles(written).equals(withoutCurrentProfiles(json.writePretty(save)));
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private boolean step(Deque<String> from, Deque<String> to) {
        writePending(); // a change still waiting for its debounced write is what the user wants undone
        var target = from.pollFirst();
        if (target == null) {
            return false;
        }
        var now = json.writePretty(save);
        push(to, now);
        groupOpen = false;
        try {
            lastChange = HistoryChanges.describe(json.readTree(now), json.readTree(target));
        } catch (RuntimeException e) {
            lastChange = List.of();
        }
        var restored = read(target);
        // Which profile is active is not part of the history: stay on the current one where it still exists.
        save.getDevices().forEach((serial, current) -> {
            var ds = restored.getDeviceSave(serial);
            if (ds != null && ds.getProfile(current.getCurrentProfileName()).isPresent()) {
                ds.setCurrentProfileName(current.getCurrentProfileName());
            }
        });
        try {
            apply(restored);
        } catch (IOException e) {
            log.error("Unable to write the configuration", e);
        }
        return true;
    }

    /**
     * Makes {@code restored} the configuration and writes it, outside the undo history. Connected devices are
     * re-announced so each is rebuilt on its restored {@link DeviceSave} (a {@link Device} holds its own reference)
     * and relit.
     */
    private void apply(Save restored) throws IOException {
        oldVersionEncountered = null; // the file is rewritten below in the current format
        save = restored;
        loadFailed = false;
        FileUtils.writeStringToFile(fileUtil.getFile(saveFileName), json.writePretty(save), Charset.defaultCharset());
        for (var device : List.copyOf(devices.all())) {
            eventBus.fire(new DeviceScanner.DeviceConnectedEvent(device.getSerialNumber(), device.deviceType(), device.descriptor()));
        }
        eventBus.fire(new SaveEvent(save, false));
    }

    private static void push(Deque<String> stack, String state) {
        stack.addFirst(state);
        while (stack.size() > HISTORY) {
            stack.removeLast();
        }
    }

    /**
     * Remembers the file as it was before this write, unless the write only switched profiles (not an edit anyone
     * wants to undo) or continues the open group of changes (see {@link #groupOpen}).
     */
    private void recordHistory(File saveFile, String next) {
        if (!saveFile.isFile()) {
            return;
        }
        try {
            var previous = FileUtils.readFileToString(saveFile, Charset.defaultCharset());
            if (!withoutCurrentProfiles(previous).equals(withoutCurrentProfiles(next))) {
                if (!groupOpen || undo.isEmpty()) {
                    push(undo, previous); // the state before this group of changes
                }
                groupOpen = true;
                redo.clear();
            }
        } catch (IOException | RuntimeException e) {
            log.debug("Unable to record undo history", e);
        }
    }

    private JsonNode withoutCurrentProfiles(String text) {
        var tree = json.readTree(text);
        var devicesNode = tree.path("devices");
        devicesNode.forEach(d -> {
            if (d instanceof ObjectNode o) {
                o.remove("currentProfileName");
            }
        });
        return tree;
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

    /**
     * Blanks any per-control mute-override target still spelled as {@link LightingConfig#LEGACY_FOLLOW_TARGET}.
     * A blank target is what "follow this control" is written as, so leaving the wording in place would
     * name a device that does not exist — both to the mute-colour resolvers and to the lighting UI.
     * Pure (no I/O); returns {@code true} if anything changed so the caller can trigger the rewrite.
     */
    static boolean migrateMuteOverrideFollow(Save save) {
        var migrated = false;
        for (var deviceSave : save.getDevices().values()) {
            for (var profile : deviceSave.getProfiles()) {
                var lc = profile.lightingConfig();
                if (lc == null) {
                    continue;
                }
                var changed = clearLegacyFollow(lc.knobConfigs(), SingleKnobLightingConfig::getMuteOverrideDeviceOrFollow, SingleKnobLightingConfig::setMuteOverrideDeviceOrFollow);
                changed |= clearLegacyFollow(lc.sliderConfigs(), SingleSliderLightingConfig::getMuteOverrideDeviceOrFollow, SingleSliderLightingConfig::setMuteOverrideDeviceOrFollow);
                changed |= clearLegacyFollow(lc.sliderLabelConfigs(), SingleSliderLabelLightingConfig::getMuteOverrideDeviceOrFollow,
                        SingleSliderLabelLightingConfig::setMuteOverrideDeviceOrFollow);
                if (changed) {
                    profile.setLightingConfig(lc);
                    migrated = true;
                }
            }
        }
        return migrated;
    }

    private static <T> boolean clearLegacyFollow(@Nullable T[] configs, Function<T, String> target, BiConsumer<T, String> setTarget) {
        if (configs == null) {
            return false;
        }
        var changed = false;
        for (var cfg : configs) {
            if (cfg != null && LightingConfig.LEGACY_FOLLOW_TARGET.equals(target.apply(cfg))) {
                setTarget.accept(cfg, "");
                changed = true;
            }
        }
        return changed;
    }

    private void handleOldVersionEncountered() {
        if (StringUtils.isBlank(oldVersionEncountered)) {
            return;
        }
        try {
            backup();
            writeToFile(); // write file only, SaveEvent will be fired from onStart()
        } finally {
            // Clear the static flag once consumed; otherwise a second load() in the same JVM would see a
            // stale value and write a spurious backup for a save that did not actually need migrating.
            // (Reset after backup(), which reads it for the .bak filename.)
            oldVersionEncountered = null;
        }
    }

    /** Writes the in-memory configuration now if it differs from the file, recording it in the history. */
    private void writePending() {
        var saveFile = fileUtil.getFile(saveFileName);
        if (save == null || !saveFile.isFile()) {
            return;
        }
        try {
            var next = json.writePretty(save);
            if (!next.equals(FileUtils.readFileToString(saveFile, Charset.defaultCharset()))) {
                writeToFile();
            }
        } catch (IOException e) {
            log.debug("Unable to compare the configuration with its file", e);
        }
    }

    private synchronized void writeToFile() { // Synchronized: a pending debounced save may run concurrently with the shutdown write
        var saveFile = fileUtil.getFile(saveFileName);
        backups.snapshotIfDue(saveFile);
        try {
            var next = json.writePretty(save);
            recordHistory(saveFile, next);
            FileUtils.writeStringToFile(saveFile, next, Charset.defaultCharset());
            unwritten = false;
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
        announceHistory();
    }

    public void debouncedSave() {
        var first = !unwritten;
        unwritten = true;
        if (first) {
            announceHistory(); // undo is available from the moment of the change, not from its write
        }
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
        // Route through the device so the current-profile default lighting comes from the device's
        // descriptor (works for descriptor-only devices like Deej that have no DeviceType).
        return devices.getDevice(serialNum).map(Device::currentProfile);
    }

    public record SaveEvent(Save save, boolean isNew) {
    }
}
