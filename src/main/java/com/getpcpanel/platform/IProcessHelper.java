package com.getpcpanel.platform;

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
}
