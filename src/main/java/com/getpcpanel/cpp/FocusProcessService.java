package com.getpcpanel.cpp;

import java.util.Locale;
import java.util.OptionalInt;

import org.apache.commons.lang3.StringUtils;

/**
 * Resolves real operating-system process IDs for features that target a window/process (e.g. Discord screen
 * share). {@link #foregroundPid()} is implemented per OS (the focused window's process — Win32
 * {@code GetForegroundWindow} on Windows, kdotool/xdotool on Linux, {@code lsappinfo} on macOS);
 * {@link #pidForExecutable(String)} is a cross-platform lookup by executable name. Deliberately NOT derived
 * from audio sessions ({@code ISndCtrl.getRunningApplications()}), which only sees apps currently producing
 * sound and reports pid 0 on Linux/PulseAudio.
 */
public interface FocusProcessService {
    /** PID of the process owning the currently focused / foreground window, or empty if it can't be resolved. */
    OptionalInt foregroundPid();

    /** PID of a live process whose executable base name matches {@code executable} (case-insensitive), else empty. */
    default OptionalInt pidForExecutable(String executable) {
        var target = baseName(executable);
        if (target == null) {
            return OptionalInt.empty();
        }
        return ProcessHandle.allProcesses()
                .filter(p -> p.info().command().map(FocusProcessService::baseName).map(target::equals).orElse(false))
                .mapToInt(p -> (int) p.pid())
                .findFirst();
    }

    /** Lowercased executable name without directory or extension, so a full path and a bare name compare equal. */
    private static String baseName(String s) {
        if (StringUtils.isBlank(s)) {
            return null;
        }
        var name = s.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        var dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name.toLowerCase(Locale.ROOT);
    }
}
