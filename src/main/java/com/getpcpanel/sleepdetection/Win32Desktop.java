package com.getpcpanel.sleepdetection;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

/**
 * JNA binding for the Win32 desktop functions JNA's bundled {@code User32} omits, used to detect
 * workstation lock/unlock without any JNA {@code Callback} (callbacks crash the native image).
 *
 * <p>When the workstation is locked the input desktop becomes the SYSTEM-owned secure "Winlogon"
 * desktop, which a normal user process cannot open: {@link #OpenInputDesktop} then returns
 * {@code NULL}. When unlocked it returns a valid handle (which we immediately {@link #CloseDesktop}).
 * Polling the result and watching for transitions is a downcall-only lock detector.
 *
 * <p>Must be {@code --initialize-at-run-time} in the native image (it calls {@code Native.load}) and
 * registered for reflection (see {@code JnaWin32ReflectionConfig}) plus proxy-config.json.
 */
public interface Win32Desktop extends StdCallLibrary {
    Win32Desktop INSTANCE = Native.load("user32", Win32Desktop.class, W32APIOptions.DEFAULT_OPTIONS);

    int DESKTOP_SWITCHDESKTOP = 0x0100;

    /** Returns the input desktop handle, or {@code null} if it cannot be opened (e.g. locked). */
    Pointer OpenInputDesktop(int dwFlags, boolean fInherit, int dwDesiredAccess);

    boolean CloseDesktop(Pointer hDesktop);
}
