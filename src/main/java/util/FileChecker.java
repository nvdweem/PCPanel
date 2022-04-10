package util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

import lombok.extern.slf4j.Slf4j;
import main.Window;

@Slf4j
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
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        System.out.println("FILE checker started");
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
            e.printStackTrace();
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
                e.printStackTrace();
            }
        }
    }
}

