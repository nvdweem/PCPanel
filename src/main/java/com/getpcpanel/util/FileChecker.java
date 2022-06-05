package com.getpcpanel.util;

import static com.getpcpanel.util.FileUtil.FILES_ROOT;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.context.ApplicationEventPublisher;

import com.getpcpanel.ui.HomePage;

import lombok.Setter;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class FileChecker extends Thread {
    private static final File REOPEN_FILE = new File(FILES_ROOT, "reopen.txt");
    private static final File LOCK_FILE = new File(FILES_ROOT, "lock.txt");
    private static final AtomicBoolean started = new AtomicBoolean(false);
    @SuppressWarnings("FieldCanBeLocal") // If this field is local then the lock will be released.
    private RandomAccessFile randomFile;
    @Setter private ApplicationEventPublisher eventPublisher;

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
        if (!REOPEN_FILE.delete()) {
            log.trace("Unable to delete {}", REOPEN_FILE);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> started.set(false), "FileChecker shutdown hook"));
    }

    private boolean isDuplicate() throws IOException {
        randomFile = new RandomAccessFile(LOCK_FILE, "rw");
        var channel = randomFile.getChannel();
        var lock = channel.tryLock();
        return lock == null;
    }

    private static void showOtherAndExit() throws IOException {
        if (!REOPEN_FILE.createNewFile()) {
            log.debug("Unable to create reopen file.");
        }
        //noinspection CallToSystemExit
        System.exit(0);
    }

    private static void tryCreateLockFile() {
        try {
            if (!LOCK_FILE.exists()) {
                if (!LOCK_FILE.createNewFile()) {
                    log.debug("Unable to create lock file.");
                }
            }
        } catch (IOException e) {
            log.error("Unable to create lock file {}, allowing duplicate instances.", LOCK_FILE, e);
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
        var folder = REOPEN_FILE.getParentFile().toPath();
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
                    if (file.toString().equals(REOPEN_FILE.getName())) {
                        if (!REOPEN_FILE.delete()) {
                            log.trace("Unable to delete {}", file);
                        }
                        log.debug("Showing window because another process was started");
                        if (eventPublisher != null) {
                            eventPublisher.publishEvent(new HomePage.ShowMainEvent());
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
