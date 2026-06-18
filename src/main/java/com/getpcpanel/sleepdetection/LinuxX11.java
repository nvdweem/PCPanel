package com.getpcpanel.sleepdetection;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.ShortByReference;

/**
 * Minimal JNA bindings for X11 display-power (DPMS) querying, used by {@link LinuxDisplayPowerMonitor}
 * to learn when the monitors are powered off/on. Pure downcalls (no JNA {@code Callback}, which crashes
 * the native image): the monitor polls {@link LibXext#DPMSInfo}.
 *
 * <p>Both interfaces call {@code Native.load} in their initializer, so they must be
 * {@code --initialize-at-run-time} and registered in {@code proxy-config.json} for the native image.
 * This is X11-only — Wayland sessions without XWayland, or compositors that don't implement DPMS, are
 * simply reported as "not capable" and the monitor stays idle.
 */
public final class LinuxX11 {
    private LinuxX11() {
    }

    /** Display power levels returned by {@link LibXext#DPMSInfo}. */
    public static final short DPMS_MODE_ON = 0;

    public interface LibX11 extends Library {
        LibX11 INSTANCE = Native.load("X11", LibX11.class);

        /** Opens the display named by {@code displayName} (use {@code null} for {@code $DISPLAY}); {@code null} on failure. */
        Pointer XOpenDisplay(String displayName);

        int XCloseDisplay(Pointer display);
    }

    public interface LibXext extends Library {
        LibXext INSTANCE = Native.load("Xext", LibXext.class);

        boolean DPMSQueryExtension(Pointer display, IntByReference eventBase, IntByReference errorBase);

        boolean DPMSCapable(Pointer display);

        /**
         * Writes the current power level (0 = {@link #DPMS_MODE_ON}, 1 = standby, 2 = suspend, 3 = off)
         * into {@code powerLevel} and the enabled flag into {@code state}. Returns non-zero on success.
         */
        int DPMSInfo(Pointer display, ShortByReference powerLevel, IntByReference state);
    }
}
