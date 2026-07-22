package com.getpcpanel.sleepdetection;

import java.util.concurrent.atomic.AtomicBoolean;
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
import com.sun.jna.platform.win32.WinUser.MSG;
import com.sun.jna.platform.win32.WinUser.WNDCLASSEX;
import com.sun.jna.platform.win32.WinUser.WindowProc;
import com.sun.jna.platform.win32.Wtsapi32;

import lombok.extern.log4j.Log4j2;

/**
 * Hosts the one hidden Windows window that receives the session/power window messages and turns them
 * into {@link SystemEventType} events for the cases the rest of the Windows detection cannot see:
 *
 * <ul>
 *   <li>the workstation being locked/unlocked — {@link SystemEventType#locked} /
 *       {@link SystemEventType#unlocked} — via {@code WTSRegisterSessionNotification}. This is the
 *       <em>authoritative</em> source: the OS states the transition instead of it being inferred from
 *       {@code OpenInputDesktop} failing, which also fails for unrelated reasons and produced spurious
 *       blackouts (issue #145). {@link WindowsSystemEventService}'s poller stands down once this is
 *       registered;</li>
 *   <li>the console display being powered off/on — {@link SystemEventType#displayOff} /
 *       {@link SystemEventType#displayOn} — via {@code GUID_CONSOLE_DISPLAY_STATE}. Covers everything
 *       the OS drives the monitors with (idle-timeout, a manual "turn off display", DPMS); it cannot
 *       see a monitor switched off at its own power button (the OS is never told);</li>
 *   <li>the system being <em>about to</em> suspend — {@link SystemEventType#goingToSuspend} — via
 *       {@code RegisterSuspendResumeNotification}, plus resuming ({@link SystemEventType#resumedFromSuspend}).
 *       The suspend half is the advance notice Windows otherwise never gives a background process;
 *       handling the resume half here makes resume prompt even for a short suspend (the cross-platform
 *       {@link SuspendResumeWatchdog} only notices a resume after the wall clock jumps past its
 *       threshold, so it stays as a fallback).</li>
 * </ul>
 *
 * <p>These arrive only as window messages, so unlike the rest of the Windows detection this needs a
 * real window with a real window procedure. It runs its own message-pump thread, mirroring the native
 * {@code FocusListener}. The {@link WindowProc} reference is held in a field so the GC never collects
 * the live native callback.
 *
 * <p><b>Not</b> a message-only window ({@code HWND_MESSAGE} parent): those never receive the
 * {@code PBT_APMSUSPEND} broadcast, and JNA's {@code WinUser.HWND_MESSAGE} constant is unusable on
 * 64-bit anyway — it is built with {@code Pointer.createConstant(int)}, which zero-extends {@code -3}
 * to {@code 0x00000000FFFFFFFD} instead of {@code 0xFFFFFFFFFFFFFFFD}, so every {@code CreateWindowEx}
 * call failed with {@code ERROR_INVALID_WINDOW_HANDLE} (1400) and this whole monitor never started.
 * A plain top-level window is created instead and simply never shown (no {@code WS_VISIBLE}, never
 * passed to {@code ShowWindow}).
 */
@Log4j2
public class WindowsPowerEventMonitor {
    // {6FE69556-704A-47A0-8F24-C28D936FDA47}
    private static final GUID GUID_CONSOLE_DISPLAY_STATE = new GUID("{6FE69556-704A-47A0-8F24-C28D936FDA47}");
    private static final String WINDOW_CLASS = "PcPanelPowerEventMonitor";

    private static final int WM_DESTROY = 0x0002;
    private static final int WM_CLOSE = 0x0010;
    private static final int WM_POWERBROADCAST = 0x0218;
    private static final int WM_WTSSESSION_CHANGE = 0x02B1;
    private static final int PBT_APMSUSPEND = 0x0004;
    private static final int PBT_APMRESUMESUSPEND = 0x0007;
    private static final int PBT_APMRESUMEAUTOMATIC = 0x0012;
    private static final int PBT_POWERSETTINGCHANGE = 0x8013;

    // POWERBROADCAST_SETTING.Data[0] for GUID_CONSOLE_DISPLAY_STATE
    private static final int DISPLAY_OFF = 0;
    private static final int DISPLAY_ON = 1;
    // Byte offsets within POWERBROADCAST_SETTING: GUID PowerSetting; DWORD DataLength; UCHAR Data[1].
    private static final int OFFSET_DATA_LENGTH = 16;
    private static final int OFFSET_DATA = 20;

    private final Consumer<SystemEventType> sink;
    /** Set once WTS session notifications are live, so the inference-based lock poller can stand down. */
    private final AtomicBoolean sessionNotificationsActive = new AtomicBoolean();
    /** Guards the registration-time echo of the console display state — see handlePowerSettingChange. */
    private final AtomicBoolean displayStateBaselineSeen = new AtomicBoolean();
    private Thread thread;
    private volatile HWND hwnd;
    private WindowProc wndProc; // strong reference: a collected callback would crash the native dispatch
    private HANDLE displayNotifyHandle;
    private HANDLE suspendNotifyHandle;
    private volatile boolean sessionNotifyRegistered;

    public WindowsPowerEventMonitor(Consumer<SystemEventType> sink) {
        this.sink = sink;
    }

    /**
     * Whether the OS is delivering authoritative lock/unlock events to this window. While true the
     * {@code OpenInputDesktop} poller must not report lock transitions of its own — it only guesses,
     * and its false positives black out the panels (#145).
     */
    public boolean hasAuthoritativeLockEvents() {
        return sessionNotificationsActive.get();
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
        } finally {
            // The pump is gone, so no more session events will arrive. Hand lock detection back to the
            // poller rather than leaving it stood down with no source at all.
            sessionNotificationsActive.set(false);
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

        // A plain top-level window with no parent: it is never shown (no WS_VISIBLE, never passed to
        // ShowWindow), but unlike a message-only window it does receive the PBT_APMSUSPEND broadcast.
        hwnd = User32.INSTANCE.CreateWindowEx(0, WINDOW_CLASS, WINDOW_CLASS, 0, 0, 0, 0, 0,
                null, null, hInst, null);
        if (hwnd == null) {
            throw new IllegalStateException("CreateWindowEx failed (err=" + Kernel32.INSTANCE.GetLastError() + ")");
        }

        // Authoritative lock/unlock. Best-effort: without it WindowsSystemEventService falls back to
        // polling OpenInputDesktop, which is why this must not abort the rest of the monitor.
        if (Wtsapi32.INSTANCE.WTSRegisterSessionNotification(hwnd, Wtsapi32.NOTIFY_FOR_THIS_SESSION)) {
            sessionNotifyRegistered = true;
            sessionNotificationsActive.set(true);
        } else {
            log.warn("WTSRegisterSessionNotification failed (err={}); falling back to polled lock detection",
                    Kernel32.INSTANCE.GetLastError());
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

        log.info("Windows power-event detection started (session notifications: {})",
                sessionNotifyRegistered ? "on" : "off - polling for lock state");

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
            case WM_WTSSESSION_CHANGE -> {
                handleSessionChange(wParam);
                return new LRESULT(0);
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

    /**
     * The OS telling us the session was locked/unlocked (or the user logged on/off). Unlike the
     * {@code OpenInputDesktop} poller this never guesses, so it cannot produce the spurious lock that
     * blacked out the panels at startup in #145. Console-connect/disconnect and remote-session events
     * are ignored — they say nothing about the panels.
     */
    private void handleSessionChange(WPARAM wParam) {
        switch (wParam.intValue()) {
            case Wtsapi32.WTS_SESSION_LOCK -> sink.accept(SystemEventType.locked);
            case Wtsapi32.WTS_SESSION_UNLOCK -> sink.accept(SystemEventType.unlocked);
            case Wtsapi32.WTS_SESSION_LOGON -> sink.accept(SystemEventType.logon);
            case Wtsapi32.WTS_SESSION_LOGOFF -> sink.accept(SystemEventType.logoff);
            default -> { /* console/remote connect-disconnect and the rest say nothing about lighting */ }
        }
    }

    private void handlePowerBroadcast(WPARAM wParam, LPARAM lParam) {
        switch (wParam.intValue()) {
            case PBT_APMSUSPEND -> sink.accept(SystemEventType.goingToSuspend);
            // RegisterSuspendResumeNotification already delivers the resume broadcasts to this window.
            // Route them to a prompt resume so a short suspend (<~11s, below the SuspendResumeWatchdog's
            // wall-clock threshold) still clears the suspend gate — otherwise the panels stay dark.
            case PBT_APMRESUMESUSPEND, PBT_APMRESUMEAUTOMATIC -> sink.accept(SystemEventType.resumedFromSuspend);
            case PBT_POWERSETTINGCHANGE -> handlePowerSettingChange(lParam);
            default -> { /* other power events (battery, low-power, …) are ignored */ }
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
        // RegisterPowerSettingNotification echoes the *current* value a few ms after registering
        // (measured: +4ms). That echo is a baseline, not a transition — acting on an "off" echo would
        // darken the panels with no matching displayOn to ever follow, which is the #145 failure mode
        // by another route. Adopt it silently; only real changes after it drive the lighting.
        if (displayStateBaselineSeen.compareAndSet(false, true)) {
            log.debug("Initial console display state: {}", state);
            return;
        }
        if (state == DISPLAY_OFF) {
            sink.accept(SystemEventType.displayOff);
        } else if (state == DISPLAY_ON) {
            sink.accept(SystemEventType.displayOn);
        }
        // state == 2 (dimmed) is left alone: the display is still on, just idle-dimming.
    }

    private void unregisterNotifications() {
        if (sessionNotifyRegistered) {
            sessionNotificationsActive.set(false);
            sessionNotifyRegistered = false;
            Wtsapi32.INSTANCE.WTSUnRegisterSessionNotification(hwnd);
        }
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
