package com.getpcpanel.util;

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
public class FileChecker extends Thread {
    private static final AtomicBoolean started = new AtomicBoolean(false);
    @SuppressWarnings("FieldCanBeLocal") // If this field is local then the lock will be released.
    private RandomAccessFile randomFile;

    // This is not a bean so don't configure the root, just pick the default. Resolve it lazily at run time:
    // a static-final new File(System.getProperty("user.home"), ...) would be frozen to the build machine's
    // home in a native image. See PCPanelHome.
    public static File getFilesRoot() {
        return PCPanelHome.resolve();
    }

    private static File reopenFile() {
        return new File(getFilesRoot(), "reopen.txt");
    }

    private static File lockFile() {
        return new File(getFilesRoot(), "lock.txt");
    }

    public static void createAndStart() {
        if (started.getAndSet(true)) {
            log.error("Trying to start FileChecker when it is already started.");
            return;
        }

        tryCreateLockFile();
        var result = new FileChecker();
        try {
            if (result.isDuplicate()) {
                log.warn("Application already running, exiting and showing the already started instance.");
                showOtherAndExit();
            }
        } catch (IOException e) {
            log.warn("Unable to determine if the application is already running, pretending it isn't.", e);
        }
        result.start();
    }

    public FileChecker() {
        super("File Checker Thread");
        setDaemon(true);
        var reopenFile = reopenFile();
        if (!reopenFile.delete()) {
            log.trace("Unable to delete {}", reopenFile);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> started.set(false), "FileChecker shutdown hook"));
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
        var lockFile = lockFile();
        try {
            // Ensure the data directory exists; FileChecker can run before FileUtil has created it.
            var root = lockFile.getParentFile();
            if (root != null && !root.exists() && !root.mkdirs()) {
                log.debug("Unable to create data directory {}", root);
            }
            if (!lockFile.exists()) {
                if (!lockFile.createNewFile()) {
                    log.debug("Unable to create lock file.");
                }
            }
        } catch (IOException e) {
            log.error("Unable to create lock file {}, allowing duplicate instances.", lockFile, e);
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
        var reopenFile = reopenFile();
        var folder = reopenFile.getParentFile().toPath();
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
                    if (file.toString().equals(reopenFile.getName())) {
                        if (!reopenFile.delete()) {
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
