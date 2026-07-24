package com.getpcpanel.util.io;

import com.getpcpanel.util.app.ShowMainEvent;
import com.getpcpanel.util.app.AppEvents;
import com.getpcpanel.util.concurrent.AppThreads;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class FileChecker implements Runnable {
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
     * already holds it, signal that instance to raise its window and exit <em>this</em> process
     * immediately. This runs before {@code Quarkus.run()} on purpose: the device layer connects to the
     * hardware on the container's {@code StartupEvent}, so a second launch that got as far as booting
     * would open the shared PCPanel and then, on its own shutdown, extinguish the LEDs of the
     * still-running first instance. Exiting here keeps a second launch from ever touching the device.
     */
    public static void ensureSingleInstance() {
        if (started.getAndSet(true)) {
            log.error("Trying to start FileChecker when it is already started.");
            return;
        }

        tryCreateLockFile();
        var checker = new FileChecker();
        instance = checker;
        try {
            if (checker.isDuplicate()) {
                log.warn("Application already running, exiting and showing the already started instance.");
                showOtherAndExit();
            }
        } catch (IOException e) {
            log.warn("Unable to determine if the application is already running, pretending it isn't.", e);
        }
    }

    /**
     * Phase 2, after the container is up: start watching for a later relaunch so this (surviving)
     * instance can raise its window. Deferred until CDI is ready because a detected relaunch fires a
     * {@link ShowMainEvent} handled by an {@code @Observes} bean. No-op when {@link #ensureSingleInstance()}
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

    private static void showOtherAndExit() throws IOException {
        if (!reopenFile().createNewFile()) {
            log.debug("Unable to create reopen file.");
        }
        //noinspection CallToSystemExit
        System.exit(0);
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
                        if (!reopenFile().delete()) {
                            log.trace("Unable to delete {}", file);
                        }
                        log.debug("Showing window because another process was started");
                        AppEvents.fire(new ShowMainEvent());
                    }
                }
            } catch (Exception e) {
                log.error("Error in checking file", e);
            }
        }
        log.info("File Checker ended");
    }
}
