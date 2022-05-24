package util;

import lombok.extern.log4j.Log4j2;
import main.Window;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.*;

@Log4j2
public class FileChecker implements Runnable {
    private static final File REOPEN_FILE = new File("reopen.txt");

    private static FileChecker fc;

    public static void start() {
        if (fc != null)
            throw new IllegalAccessError("Cannot start file checker thread more than once");
        REOPEN_FILE.delete();
        fc = new FileChecker();
        new Thread(fc, "File Checker Thread").start();
    }

    public static void checkIsDuplicateRunning() {
        try {
            File lockFile = new File("lock.txt");
            if (!lockFile.exists())
                lockFile.createNewFile();

            try (RandomAccessFile randomFile = new RandomAccessFile(lockFile, "rw")) {
                FileChannel channel = randomFile.getChannel();
                if (channel.tryLock() == null) {
                    log.warn("Sorry but you already have a running instance of the application");
                    REOPEN_FILE.createNewFile();
                    System.exit(0);
                }
            }
        } catch (Exception e) {
            log.error("Unable to check if duplicate is running", e);
        }
    }

    @Override
    public void run() {
        log.info("FILE checker started");
        WatchService watcher = null;
        try {
            watcher = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            log.error("Unable to start watch service", e);
            return;
        }
        Path folder = new File("").toPath();
        WatchKey watchkey = null;
        try {
            watchkey = folder.register(watcher, StandardWatchEventKinds.ENTRY_CREATE);
        } catch (IOException e) {
            log.error("Unable to register for event in file checker", e);
        }
        while (true) {
            try {
                WatchKey key = watcher.take();
                key.reset();
                if (key != watchkey)
                    continue;
                for (WatchEvent<?> event : watchkey.pollEvents()) {
                    Path file = (Path) event.context();
                    if (file.toString().equals(REOPEN_FILE.getName())) {
                        file.toFile().delete();
                        Window.reopen();
                    }
                }
            } catch (Exception e) {
                log.error("Error in checking file", e);
            }
        }
    }
}

