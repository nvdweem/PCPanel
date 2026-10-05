package com.getpcpanel.appwindow;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.apache.commons.lang3.SystemUtils;

import com.getpcpanel.util.io.FileUtil;
import com.getpcpanel.util.os.ProcessHelper;
import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;

import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * Runs the app window: this application's own executable started again as {@link AppWindowMain}, a process that is
 * a web view on the UI and nothing more. At most one runs at a time; showing the UI while it is open raises it.
 * A window that cannot be shown here (no web view on the system, or it dies while starting) is reported to the
 * caller, which opens the browser instead.
 */
@Log4j2
@ApplicationScoped
public class AppWindowService {
    /** A window that ends this soon after it started, other than by being closed, did not get to show. */
    private static final Duration STARTUP_GRACE = Duration.ofSeconds(10);

    @Inject ProcessHelper processes;
    @Inject FileUtil fileUtil;

    /** Windows this service closed itself; one of these ending is never a failure to show. */
    private final Set<Process> closedOnPurpose = ConcurrentHashMap.newKeySet();
    private @Nullable Process window;
    private @Nullable Writer input;

    /** Whether this platform has an app window. */
    public static boolean isSupported() {
        return SystemUtils.IS_OS_WINDOWS || SystemUtils.IS_OS_LINUX;
    }

    /**
     * Shows the UI in the app window, starting it when none is open. An open window keeps its page unless
     * {@code navigate}, and is raised either way. {@code onUnavailable} runs, on another thread, when the window
     * cannot be shown.
     */
    public synchronized void show(Supplier<String> url, boolean navigate, Runnable onUnavailable) {
        if (window != null && window.isAlive() && input != null) {
            allowForeground(window);
            if (send(navigate ? "show " + url.get() : "raise")) {
                return;
            }
        }
        try {
            start(url.get(), onUnavailable);
        } catch (IOException e) {
            log.warn("Could not start the app window; opening the browser instead", e);
            onUnavailable.run();
        }
    }

    /** Closes the app window, if one is open. */
    public synchronized void close() {
        if (window != null && window.isAlive()) {
            var closing = window;
            closedOnPurpose.add(closing);
            send("close");
            closing.onExit().orTimeout(5, TimeUnit.SECONDS)
                   .exceptionally(timeout -> {
                       ProcessHelper.stop(closing);
                       return closing;
                   });
        }
        forget();
    }

    public synchronized boolean isOpen() {
        return window != null && window.isAlive();
    }

    /** The window closes with the application: the end of its input tells it to. */
    void onShutdown(@Observes ShutdownEvent event) {
        synchronized (this) {
            closeInput();
        }
    }

    private void start(String url, Runnable onUnavailable) throws IOException {
        var root = fileUtil.getRoot().toPath();
        var dataDir = root.resolve("appwindow");
        var logs = root.resolve("logs");
        Files.createDirectories(dataDir);
        Files.createDirectories(logs);
        var errorLog = logs.resolve("appwindow.log").toFile();
        var process = processes.startWithInput(errorLog, command(dataDir));
        var started = Instant.now();
        window = process;
        input = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
        if (!send(url)) {
            ProcessHelper.stop(process);
            forget();
            throw new IOException("The app window did not take its URL");
        }
        log.info("Opened the app window (pid {})", process.pid());
        process.onExit().thenAccept(ended -> onExit(ended, started, errorLog, onUnavailable));
    }

    private void onExit(Process ended, Instant started, File errorLog, Runnable onUnavailable) {
        synchronized (this) {
            if (window == ended) {
                forget();
            }
        }
        var status = ended.exitValue();
        var unavailable = !closedOnPurpose.remove(ended)
                && (status == AppWindowMain.EXIT_UNAVAILABLE
                || (status != 0 && Duration.between(started, Instant.now()).compareTo(STARTUP_GRACE) < 0));
        if (unavailable) {
            log.warn("The app window could not be shown (exit status {}, see {}); opening the browser instead", status, errorLog);
            onUnavailable.run();
        } else {
            log.info("The app window closed (exit status {})", status);
        }
    }

    /**
     * The command that starts the app window: the native executable with {@link AppWindowMain#ARG}, or on the JVM
     * (dev mode) a JVM running {@link AppWindowMain} with JNA, the only library it needs, on its class path.
     */
    private static String[] command(Path dataDir) throws IOException {
        var self = ProcessHandle.current().info().command().orElseThrow(() -> new IOException("Unknown executable"));
        if ("runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))) {
            return new String[] { self, AppWindowMain.ARG, dataDir.toString() };
        }
        var classPath = new ArrayList<String>();
        for (var type : List.of(AppWindowMain.class, Native.class, User32.class)) {
            try {
                classPath.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
            } catch (Exception e) {
                throw new IOException("Cannot locate " + type.getName() + " for the app window", e);
            }
        }
        return new String[] { self, "--enable-native-access=ALL-UNNAMED", "-cp", String.join(File.pathSeparator, classPath),
                AppWindowMain.class.getName(), dataDir.toString() };
    }

    private boolean send(String line) {
        try {
            input.write(line);
            input.write('\n');
            input.flush();
            return true;
        } catch (IOException | RuntimeException e) {
            log.debug("Could not reach the app window", e);
            return false;
        }
    }

    /** Lets the window come to the front: Windows only lets the process the user just clicked do that, so pass it on. */
    private static void allowForeground(Process process) {
        if (SystemUtils.IS_OS_WINDOWS) {
            Function.getFunction("user32", "AllowSetForegroundWindow").invokeInt(new Object[] { (int) process.pid() });
        }
    }

    private void forget() {
        closeInput();
        window = null;
    }

    private void closeInput() {
        if (input != null) {
            try {
                input.close();
            } catch (IOException e) {
                log.trace("Closing the app window's input", e);
            }
            input = null;
        }
    }
}
