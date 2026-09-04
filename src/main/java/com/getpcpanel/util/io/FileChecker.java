package com.getpcpanel.util.io;

import com.getpcpanel.util.app.ShowMainEvent;
import com.getpcpanel.util.app.AppEvents;
import com.getpcpanel.util.concurrent.AppThreads;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class FileChecker implements Runnable {
    /** Marker contents naming what kind of launch asked the running instance to react. */
    private static final String LAUNCH_USER = "user";
    private static final String LAUNCH_AUTOSTART = "autostart";
    private static final AtomicBoolean started = new AtomicBoolean(false);
    @SuppressWarnings("StaticNonFinalField") // The instance that holds the single-instance lock, retained across the two lifecycle phases.
    private static volatile FileChecker instance;
    @SuppressWarnings("FieldCanBeLocal") // If this field is local then the lock will be released.
    private RandomAccessFile randomFile;

    // Resolved at call time (runtime), never cached in a static field: a static initializer runs at
    // native-image BUILD time, which would bake in the build machine's user.home (e.g. the CI runner's
    // C:\Users\runneradmin). This is not a bean, so it goes through PcPanelRoot directly (same root the
    // pcpanel.root config property resolves to) rather than reading the config.
    private static File filesRoot() {
        var root = PcPanelRoot.resolve().toFile();
        if (!root.isDirectory()) {
            //noinspection ResultOfMethodCallIgnored
            root.mkdirs();
        }
        return root;
    }

    private static File reopenFile() {
        return new File(filesRoot(), "reopen.txt");
    }

    private static File lockFile() {
        return new File(filesRoot(), "lock.txt");
    }

    /**
     * Phase 1, before the Quarkus container starts: take the single-instance lock. If another instance
     * already holds it, tell that instance what kind of launch this was and exit <em>this</em> process
     * immediately; it decides whether to raise its UI. This runs before {@code Quarkus.run()} on
     * purpose: the device layer connects to the hardware on the container's {@code StartupEvent}, so a
     * second launch that got as far as booting would open the shared PCPanel and then, on its own
     * shutdown, extinguish the LEDs of the still-running first instance. Exiting here keeps a second
     * launch from ever touching the device.
     *
     * @param userLaunch whether a person started this process (a shortcut, the Start menu, the
     *                   installer) as opposed to the OS starting it at logon — see
     *                   {@link #signalRunningInstance(boolean, File)}.
     */
    public static void ensureSingleInstance(boolean userLaunch) {
        if (started.getAndSet(true)) {
            log.error("Trying to start FileChecker when it is already started.");
            return;
        }

        tryCreateLockFile();
        var checker = new FileChecker();
        instance = checker;
        try {
            if (checker.isDuplicate()) {
                log.warn("Application already running, exiting (userLaunch={}).", userLaunch);
                signalRunningInstanceAndExit(userLaunch);
            }
        } catch (IOException e) {
            log.warn("Unable to determine if the application is already running, pretending it isn't.", e);
        }
    }

    /**
     * Phase 2, after the container is up: start watching for a later relaunch so this (surviving)
     * instance can raise its window. Deferred until CDI is ready because a detected relaunch fires a
     * {@link ShowMainEvent} handled by an {@code @Observes} bean. No-op when {@link #ensureSingleInstance(boolean)}
     * did not run (e.g. the {@code skipfilecheck} arg), so nothing holds the lock and there is nothing to watch.
     */
    public static void startWatching() {
        var checker = instance;
        if (checker == null) {
            return;
        }
        AppThreads.named("File Checker Thread", true, checker).start();
    }

    public FileChecker() {
        if (!reopenFile().delete()) {
            log.trace("Unable to delete {}", reopenFile());
        }

        // Raw JVM hook on purpose: FileChecker is created from Main before CDI starts, so it cannot
        // observe the Quarkus ShutdownEvent. The hook only clears the started flag.
        Runtime.getRuntime().addShutdownHook(AppThreads.named("FileChecker shutdown hook", false, () -> started.set(false)));
    }

    private boolean isDuplicate() throws IOException {
        randomFile = new RandomAccessFile(lockFile(), "rw");
        var channel = randomFile.getChannel();
        var lock = channel.tryLock();
        return lock == null;
    }

    private static void signalRunningInstanceAndExit(boolean userLaunch) throws IOException {
        signalRunningInstance(userLaunch, reopenFile());
        //noinspection CallToSystemExit
        System.exit(0);
    }

    /**
     * Tell the already-running instance what this duplicate launch was, by writing the kind into the
     * marker its watcher reacts to. The running instance decides what to do with it — this process
     * exits before the container (and so before the file log handler) exists, so anything it decided
     * for itself would leave no record of why the UI did or did not open.
     *
     * <p>The marker is written elsewhere and moved into place: the watcher reacts to the file being
     * created, so writing the contents afterwards would race with it reading an empty file. The
     * temporary name is not the one the watcher matches, so moving it in is the first event it sees.
     */
    static void signalRunningInstance(boolean userLaunch, File reopen) throws IOException {
        var kind = userLaunch ? LAUNCH_USER : LAUNCH_AUTOSTART;
        var staged = new File(reopen.getParentFile(), reopen.getName() + ".tmp");
        Files.writeString(staged.toPath(), kind);
        Files.move(staged.toPath(), reopen.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Whether a duplicate launch described by this marker should raise the UI.
     *
     * <p>Only a launch the user performed themselves is a "show me the UI" gesture. An autostart launch
     * is the OS starting PCPanel at logon, and a machine can do that twice — two autostart registrations
     * left behind by switching between the plain and the administrator startup option, or Windows
     * restoring the previous session's apps alongside one of them. Treating that second launch as a
     * gesture opens the browser on every boot, which overrides the user's "open in browser when PCPanel
     * starts" preference and looks like that setting is being ignored.
     *
     * <p>Anything else, including an unreadable or empty marker, counts as a user launch: raising the UI
     * on a gesture we could not classify is the friendlier way to be wrong.
     */
    static boolean shouldShowUi(@Nullable String marker) {
        return !LAUNCH_AUTOSTART.equals(StringUtils.trimToEmpty(marker));
    }

    /** Reads the marker a duplicate launch left, and removes it. Empty when it could not be read. */
    private static String consumeMarker() {
        var reopen = reopenFile();
        try {
            return Files.exists(reopen.toPath()) ? Files.readString(reopen.toPath()) : "";
        } catch (IOException e) {
            log.warn("Unable to read {}, treating the second instance as a user launch.", reopen, e);
            return "";
        } finally {
            if (!reopen.delete()) {
                log.trace("Unable to delete {}", reopen);
            }
        }
    }

    private static void tryCreateLockFile() {
        try {
            if (!lockFile().exists()) {
                if (!lockFile().createNewFile()) {
                    log.debug("Unable to create lock file.");
                }
            }
        } catch (IOException e) {
            log.error("Unable to create lock file {}, allowing duplicate instances.", lockFile(), e);
        }
    }

    @Override
    public void run() {
        log.info("File checker started");
        WatchService watcher;
        try {
            watcher = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            log.error("Unable to start watch service", e);
            return;
        }
        var folder = reopenFile().getParentFile().toPath();
        WatchKey watchkey = null;
        try {
            watchkey = folder.register(watcher, StandardWatchEventKinds.ENTRY_CREATE);
        } catch (IOException e) {
            log.error("Unable to register for event in file checker", e);
        }
        while (started.get()) {
            try {
                var key = watcher.take();
                key.reset();
                if (!key.equals(watchkey))
                    continue;
                for (var event : watchkey.pollEvents()) {
                    var file = (Path) event.context();
                    if (file.toString().equals(reopenFile().getName())) {
                        // INFO, not DEBUG: together with StartupOnboarding's line this is the only record
                        // of whether the app opened the browser and why, and shipped builds log at INFO.
                        var marker = consumeMarker();
                        if (shouldShowUi(marker)) {
                            log.info("A second instance was started by the user; showing the UI.");
                            AppEvents.fire(new ShowMainEvent());
                        } else {
                            log.info("A second instance was started by the system at logon; leaving the UI closed.");
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error in checking file", e);
            }
        }
        log.info("File Checker ended");
    }
}
