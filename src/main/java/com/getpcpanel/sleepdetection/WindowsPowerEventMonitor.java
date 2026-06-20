package com.getpcpanel.sleepdetection;

import java.util.function.Consumer;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HINSTANCE;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.MSG;
import com.sun.jna.platform.win32.WinUser.WNDCLASSEX;
import com.sun.jna.platform.win32.WinUser.WindowProc;

import lombok.extern.log4j.Log4j2;

/**
 * Hosts the one hidden Windows window that receives {@code WM_POWERBROADCAST} and turns it into
 * {@link SystemEventType} events for two cases the rest of the callback-free Windows detection cannot
 * see:
 *
 * <ul>
 *   <li>the console display being powered off/on — {@link SystemEventType#displayOff} /
 *       {@link SystemEventType#displayOn} — via {@code GUID_CONSOLE_DISPLAY_STATE}. Covers everything
 *       the OS drives the monitors with (idle-timeout, a manual "turn off display", DPMS); it cannot
 *       see a monitor switched off at its own power button (the OS is never told);</li>
 *   <li>the system being <em>about to</em> suspend — {@link SystemEventType#goingToSuspend} — via
 *       {@code RegisterSuspendResumeNotification}. This is the advance notice Windows otherwise never
 *       gives a background process; resume-from-suspend stays with the cross-platform
 *       {@link SuspendResumeWatchdog}.</li>
 * </ul>
 *
 * <p>Both arrive only as window messages, so unlike the rest of the Windows detection this needs a
 * (message-only) window with a real window procedure. It runs its own message-pump thread, mirroring
 * the native {@code FocusListener}. The {@link WindowProc} reference is held in a field so the GC never
 * collects the live native callback.
 */
@Log4j2
public class WindowsPowerEventMonitor {
    // {6FE69556-704A-47A0-8F24-C28D936FDA47}
    private static final GUID GUID_CONSOLE_DISPLAY_STATE = new GUID("{6FE69556-704A-47A0-8F24-C28D936FDA47}");
    private static final String WINDOW_CLASS = "PcPanelPowerEventMonitor";

    private static final int WM_DESTROY = 0x0002;
    private static final int WM_CLOSE = 0x0010;
    private static final int WM_POWERBROADCAST = 0x0218;
    private static final int PBT_APMSUSPEND = 0x0004;
    private static final int PBT_POWERSETTINGCHANGE = 0x8013;

    // POWERBROADCAST_SETTING.Data[0] for GUID_CONSOLE_DISPLAY_STATE
    private static final int DISPLAY_OFF = 0;
    private static final int DISPLAY_ON = 1;
    // Byte offsets within POWERBROADCAST_SETTING: GUID PowerSetting; DWORD DataLength; UCHAR Data[1].
    private static final int OFFSET_DATA_LENGTH = 16;
    private static final int OFFSET_DATA = 20;

    private final Consumer<SystemEventType> sink;
    private Thread thread;
    private volatile HWND hwnd;
    private WindowProc wndProc; // strong reference: a collected callback would crash the native dispatch
    private HANDLE displayNotifyHandle;
    private HANDLE suspendNotifyHandle;

    public WindowsPowerEventMonitor(Consumer<SystemEventType> sink) {
        this.sink = sink;
    }

    public void start() {
        thread = new Thread(this::run, "windows-power-event-monitor");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        var h = hwnd;
        if (h != null) {
            // Ask the window to close from its own thread; the pump then sees WM_QUIT and returns.
            try {
                User32.INSTANCE.PostMessage(h, WM_CLOSE, new WPARAM(0), new LPARAM(0));
            } catch (Throwable e) { // NOSONAR - best-effort shutdown
                log.debug("Failed to post WM_CLOSE to power-event window", e);
            }
        }
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void run() {
        try {
            createWindowAndPump();
        } catch (Throwable e) { // NOSONAR - power-event detection is non-essential, must never crash the app
            log.warn("Windows power-event detection unavailable: {}", e.toString());
            log.debug("power-event monitor failure", e);
        }
    }

    private void createWindowAndPump() {
        var hInst = new HINSTANCE();
        hInst.setPointer(Kernel32.INSTANCE.GetModuleHandle(null).getPointer());

        wndProc = this::windowProc;
        var wc = new WNDCLASSEX();
        wc.lpfnWndProc = wndProc;
        wc.hInstance = hInst;
        wc.lpszClassName = WINDOW_CLASS;
        if (User32.INSTANCE.RegisterClassEx(wc).intValue() == 0) {
            throw new IllegalStateException("RegisterClassEx failed (err=" + Kernel32.INSTANCE.GetLastError() + ")");
        }

        // A message-only window (HWND_MESSAGE parent) receives messages but is never shown.
        hwnd = User32.INSTANCE.CreateWindowEx(0, WINDOW_CLASS, WINDOW_CLASS, 0, 0, 0, 0, 0,
                WinUser.HWND_MESSAGE, null, hInst, null);
        if (hwnd == null) {
            throw new IllegalStateException("CreateWindowEx failed (err=" + Kernel32.INSTANCE.GetLastError() + ")");
        }

        displayNotifyHandle = Win32PowerNotify.INSTANCE.RegisterPowerSettingNotification(
                hwnd, GUID_CONSOLE_DISPLAY_STATE, Win32PowerNotify.DEVICE_NOTIFY_WINDOW_HANDLE);
        if (displayNotifyHandle == null) {
            throw new IllegalStateException("RegisterPowerSettingNotification failed (err=" + Kernel32.INSTANCE.GetLastError() + ")");
        }

        // Advance suspend notice is best-effort: if it can't be registered, display detection and the
        // resume watchdog still work, so warn rather than abort the whole monitor.
        suspendNotifyHandle = Win32PowerNotify.INSTANCE.RegisterSuspendResumeNotification(
                hwnd, Win32PowerNotify.DEVICE_NOTIFY_WINDOW_HANDLE);
        if (suspendNotifyHandle == null) {
            log.warn("RegisterSuspendResumeNotification failed (err={}); no advance suspend notice", Kernel32.INSTANCE.GetLastError());
        }

        log.info("Windows power-event detection started");

        var msg = new MSG();
        // GetMessage returns >0 for a message, 0 on WM_QUIT, -1 on error.
        while (User32.INSTANCE.GetMessage(msg, hwnd, 0, 0) > 0) {
            User32.INSTANCE.TranslateMessage(msg);
            User32.INSTANCE.DispatchMessage(msg);
        }
    }

    private LRESULT windowProc(HWND hWnd, int uMsg, WPARAM wParam, LPARAM lParam) {
        switch (uMsg) {
            case WM_POWERBROADCAST -> {
                handlePowerBroadcast(wParam, lParam);
                return new LRESULT(1); // TRUE
            }
            case WM_CLOSE -> {
                User32.INSTANCE.DestroyWindow(hWnd);
                return new LRESULT(0);
            }
            case WM_DESTROY -> {
                unregisterNotifications();
                User32.INSTANCE.PostQuitMessage(0);
                return new LRESULT(0);
            }
            default -> {
                return User32.INSTANCE.DefWindowProc(hWnd, uMsg, wParam, lParam);
            }
        }
    }

    private void handlePowerBroadcast(WPARAM wParam, LPARAM lParam) {
        switch (wParam.intValue()) {
            case PBT_APMSUSPEND -> sink.accept(SystemEventType.goingToSuspend);
            case PBT_POWERSETTINGCHANGE -> handlePowerSettingChange(lParam);
            default -> { /* other power events (resume, battery, …) are handled elsewhere or ignored */ }
        }
    }

    private void handlePowerSettingChange(LPARAM lParam) {
        // lParam points at a POWERBROADCAST_SETTING; only act on the console-display setting.
        var setting = new Pointer(lParam.longValue());
        if (!GUID_CONSOLE_DISPLAY_STATE.equals(new GUID(setting))) {
            return;
        }
        if (setting.getInt(OFFSET_DATA_LENGTH) < 1) {
            return;
        }
        var state = setting.getByte(OFFSET_DATA) & 0xFF;
        if (state == DISPLAY_OFF) {
            sink.accept(SystemEventType.displayOff);
        } else if (state == DISPLAY_ON) {
            sink.accept(SystemEventType.displayOn);
        }
        // state == 2 (dimmed) is left alone: the display is still on, just idle-dimming.
    }

    private void unregisterNotifications() {
        if (displayNotifyHandle != null) {
            Win32PowerNotify.INSTANCE.UnregisterPowerSettingNotification(displayNotifyHandle);
            displayNotifyHandle = null;
        }
        if (suspendNotifyHandle != null) {
            Win32PowerNotify.INSTANCE.UnregisterSuspendResumeNotification(suspendNotifyHandle);
            suspendNotifyHandle = null;
        }
    }
}
