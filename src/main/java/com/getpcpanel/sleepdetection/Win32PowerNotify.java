package com.getpcpanel.sleepdetection;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

/**
 * JNA binding for the Win32 power-notification functions that JNA's bundled {@code User32} omits. Used
 * to learn when the console display is powered off/on (monitor idle-timeout, manual sleep, DPMS) and
 * when the system is about to suspend — both of which Windows only delivers as a
 * {@code WM_POWERBROADCAST} window message, so they need a real window and a window-procedure
 * {@link com.sun.jna.Callback}.
 *
 * <p>Window-proc callbacks used to segfault the GraalVM native image; that was tied to the
 * non-headless AWT toolkit, which the Windows build no longer initialises ({@code
 * -Djava.awt.headless=true}). Must be {@code --initialize-at-run-time} (it calls {@code Native.load})
 * and registered for reflection/proxy in the native image (see {@code JnaWin32ReflectionConfig} and
 * {@code proxy-config.json}).
 *
 * @see WindowsPowerEventMonitor
 */
public interface Win32PowerNotify extends StdCallLibrary {
    Win32PowerNotify INSTANCE = Native.load("user32", Win32PowerNotify.class, W32APIOptions.DEFAULT_OPTIONS);

    /** Delivers the notification via the window's message queue (vs. a direct callback). */
    int DEVICE_NOTIFY_WINDOW_HANDLE = 0x0000_0000;

    /**
     * Subscribe {@code hRecipient} (a window handle) to changes of {@code powerSetting}. Returns an
     * opaque handle to pass to {@link #UnregisterPowerSettingNotification}, or {@code null} on failure.
     */
    HANDLE RegisterPowerSettingNotification(HWND hRecipient, GUID powerSetting, int flags);

    boolean UnregisterPowerSettingNotification(HANDLE handle);

    /**
     * Subscribe {@code hRecipient} (a window handle, with {@link #DEVICE_NOTIFY_WINDOW_HANDLE}) to
     * suspend/resume notifications, delivered as {@code WM_POWERBROADCAST}. Unlike the legacy
     * {@code PBT_APMSUSPEND} broadcast — which Windows never sends to a message-only window — this
     * targets the registered window directly, so it works for our hidden monitor window. Returns an
     * opaque handle to pass to {@link #UnregisterSuspendResumeNotification}, or {@code null} on failure.
     */
    HANDLE RegisterSuspendResumeNotification(HANDLE hRecipient, int flags);

    boolean UnregisterSuspendResumeNotification(HANDLE handle);
}
