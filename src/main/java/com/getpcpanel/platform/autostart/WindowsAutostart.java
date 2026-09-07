package com.getpcpanel.platform.autostart;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.commons.lang3.SystemUtils;

import com.getpcpanel.Main;
import com.getpcpanel.rest.model.dto.AutostartStateDto;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * The "start with Windows" registration, shared with the installer: the {@code HKCU\...\Run} value named
 * {@code PCPanel} holding {@code "<exe>" quiet} (the {@code quiet} argument is what marks a launch as the
 * OS's, see {@link Main#isAutostartLaunch}), and the elevated scheduled task of the same name that the
 * installer's "run as administrator" option creates instead. The installer seeds its startup choice
 * from these on every upgrade, so a change made here survives updates.
 *
 * <p>The registry and the task are reached through {@code reg.exe}, {@code schtasks.exe} and
 * {@code powershell.exe} rather than JNA, so nothing new has to be registered for the native image. Only
 * the write needs PowerShell: the value data carries quotes, and Windows {@link ProcessBuilder} rejects
 * an argument with embedded quotes, so the script travels base64-encoded in {@code -EncodedCommand}.
 * Reads and the delete take plain arguments.
 *
 * <p>Only an installed Windows build is {@linkplain AutostartStateDto#supported() supported}: the value
 * points at the running executable, which is only PCPanel itself in the native image.
 */
@Log4j2
@ApplicationScoped
public class WindowsAutostart {
    static final String RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    static final String RUN_KEY_PS = "HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    /** Value and task name; keep in sync with {@code packaging/windows/pcpanel.iss}. */
    static final String NAME = "PCPanel";

    /** Runs a command to completion and returns its exit code; the output is not used. */
    @FunctionalInterface
    public interface CommandRunner {
        int run(List<String> command) throws IOException, InterruptedException;
    }

    public static class AutostartException extends RuntimeException {
        public AutostartException(String message) {
            super(message);
        }
    }

    private final CommandRunner runner;
    private final Supplier<Optional<String>> exePath;
    private final boolean platformSupported;

    @Inject
    public WindowsAutostart() {
        this(WindowsAutostart::exec, () -> ProcessHandle.current().info().command(), SystemUtils.IS_OS_WINDOWS && isNativeImage());
    }

    WindowsAutostart(CommandRunner runner, Supplier<Optional<String>> exePath, boolean platformSupported) {
        this.runner = runner;
        this.exePath = exePath;
        this.platformSupported = platformSupported;
    }

    public AutostartStateDto state() {
        if (!platformSupported) {
            return new AutostartStateDto(false, false, false);
        }
        var task = elevatedTaskExists();
        return new AutostartStateDto(true, task || runValueExists(), task);
    }

    /** Registers or removes the {@code HKCU\Run} value, and verifies the registry now says so. */
    public void set(boolean enabled) {
        if (!platformSupported) {
            throw new AutostartException("Start with Windows can only be changed on an installed Windows build.");
        }
        if (elevatedTaskExists()) {
            throw new AutostartException("Windows starts PCPanel through the administrator task the installer set up; run the installer to change that.");
        }
        if (enabled) {
            var exe = exePath.get().orElseThrow(() -> new AutostartException("The path of the running PCPanel executable is unknown."));
            var value = "\"" + exe + "\" " + Main.AUTOSTART_ARG;
            var script = "Set-ItemProperty -LiteralPath '" + RUN_KEY_PS + "' -Name '" + NAME + "' -Type String -Value '" + value.replace("'", "''") + "'";
            var encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
            run(List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded));
            log.info("Registered PCPanel to start with Windows: {}", value);
        } else if (runValueExists()) {
            run(List.of("reg.exe", "delete", RUN_KEY, "/v", NAME, "/f"));
            log.info("Removed the start-with-Windows registration");
        }
        if (runValueExists() != enabled) {
            throw new AutostartException("The start-with-Windows registration could not be " + (enabled ? "written." : "removed."));
        }
    }

    private boolean runValueExists() {
        return run(List.of("reg.exe", "query", RUN_KEY, "/v", NAME)) == 0;
    }

    private boolean elevatedTaskExists() {
        return run(List.of("schtasks.exe", "/Query", "/TN", NAME)) == 0;
    }

    private int run(List<String> command) {
        try {
            return runner.run(command);
        } catch (IOException e) {
            throw new AutostartException("Could not run " + command.getFirst() + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AutostartException("Interrupted while running " + command.getFirst());
        }
    }

    private static int exec(List<String> command) throws IOException, InterruptedException {
        return new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
                .waitFor();
    }

    @SuppressWarnings("AccessOfSystemProperties")
    private static boolean isNativeImage() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }
}
