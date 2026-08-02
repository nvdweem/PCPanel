package com.getpcpanel.platform;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Per-OS lookup of the foreground window's process. Implemented by the platform process helpers
 * (Windows/Linux/macOS), each gated by the build stereotype in this package. Sound control
 * ({@code ISndCtrl}) implementations may depend on this when they genuinely need a PID, but the reverse is
 * forbidden: sound control must never be the source of a PID for something that isn't producing audio (its
 * app list is audio sessions only).
 */
public interface IProcessHelper {
    /** PID of the process owning the currently focused / foreground window, or empty if it can't be resolved. */
    OptionalInt foregroundPid();

    /**
     * Ordered label → value facts explaining whether focused-window detection can work on this machine, for
     * the bug-report bundle. Empty where the OS answers the question directly and the only possible failure
     * is an OS error (Windows, macOS); Linux fills it in, because there the answer depends on the desktop
     * session and on external helper binaries that may be present but useless (#151).
     */
    default Map<String, String> focusDiagnostics() {
        return Map.of();
    }

    /**
     * One sentence the UI can show instead of a bare "could not read the focused app", or empty when there is
     * nothing more to say than that. Separate from {@link #focusDiagnostics()}: that is the evidence, this is
     * the conclusion a user can act on (install something, or accept that their desktop can't do it).
     */
    default Optional<String> focusUnavailableReason() {
        return Optional.empty();
    }
}
