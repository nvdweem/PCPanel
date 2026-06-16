package com.getpcpanel.util.tray.win;

import com.getpcpanel.platform.WindowsBuild;
import com.getpcpanel.util.ShowMainEvent;
import com.getpcpanel.util.tray.ITrayService;
import com.getpcpanel.util.tray.awt.AwtTrayImpl;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HICON;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.platform.win32.WinUser.MSG;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * Win32 system-tray implementation for Windows, used instead of {@code java.awt.SystemTray}: AWT's
 * native toolkit ({@code awt.dll}) is unavailable / unreliable in the Quarkus GraalVM native image on
 * Windows, so the AWT tray either fails to load the library or dies on a JNI field. This implementation
 * talks to the Win32 notification-area API through JNA instead.
 *
 * <p>Like {@link com.getpcpanel.overlay.Win32VolumeOverlay} it uses <b>no JNA {@code Callback}</b>
 * (native&#8594;Java upcalls crash the native image): the icon is hosted by a hidden window of the
 * built-in {@code "STATIC"} class (whose window procedure lives in user32.dll), and a dedicated thread
 * pumps the message queue. Tray notifications are posted to that window, so they are read directly off
 * the queue; the right-click menu uses {@code TrackPopupMenu(TPM_RETURNCMD)}, which returns the chosen
 * command without a {@code WM_COMMAND} window procedure.
 */
@Log4j2
@ApplicationScoped
@WindowsBuild
@AwtTrayImpl
public class TrayServiceWin implements ITrayService {
    private static final String WINDOW_CLASS = "STATIC"; // built-in window procedure; avoids a JNA callback
    private static final int WM_TRAY_CALLBACK = 0x0400 + 1; // WM_USER + 1
    private static final int TRAY_ID = 1;
    private static final int MENU_EXIT = 1;
    private static final int IDI_APPLICATION = 32512;

    // Mouse messages delivered in the low word of the callback's lParam.
    private static final int WM_LBUTTONUP = 0x0202;
    private static final int WM_LBUTTONDBLCLK = 0x0203;
    private static final int WM_RBUTTONUP = 0x0205;

    @Inject Event<Object> eventBus;

    private volatile boolean running;

    @Override
    public void init() {
        var thread = new Thread(this::run, "PCPanel Tray");
        thread.setDaemon(true);
        thread.start();
    }

    private void run() {
        try {
            runLoop();
        } catch (Throwable t) {
            // The tray must never take down the app; a missing tray icon is acceptable degradation.
            log.warn("System tray is unavailable; continuing without a tray icon ({})", t.toString());
        }
    }

    private void runLoop() {
        var user32 = User32.INSTANCE;
        var hInst = Kernel32.INSTANCE.GetModuleHandle(null);
        // Hidden message-target window using the built-in STATIC window procedure (no Java WNDPROC
        // callback, which segfaults the native image). Tray notifications are POSTED to this window
        // and read off the thread's message queue below.
        var hWnd = user32.CreateWindowEx(0, WINDOW_CLASS, "PCPanel Tray", 0, 0, 0, 0, 0, null, null, hInst, null);
        if (hWnd == null) {
            log.warn("Unable to create tray window (error {})", Kernel32.INSTANCE.GetLastError());
            return;
        }

        var nid = new WinShell32.NOTIFYICONDATA();
        nid.hWnd = hWnd;
        nid.uID = TRAY_ID;
        nid.uFlags = WinShell32.NIF_ICON | WinShell32.NIF_MESSAGE | WinShell32.NIF_TIP;
        nid.uCallbackMessage = WM_TRAY_CALLBACK;
        nid.hIcon = loadAppIcon();
        nid.setTip("PCPanel");

        if (!WinShell32.INSTANCE.Shell_NotifyIcon(WinShell32.NIM_ADD, nid)) {
            log.warn("Shell_NotifyIcon(NIM_ADD) failed (error {})", Kernel32.INSTANCE.GetLastError());
            return;
        }
        running = true;
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> WinShell32.INSTANCE.Shell_NotifyIcon(WinShell32.NIM_DELETE, nid), "PCPanel Tray cleanup"));
        log.debug("Windows tray icon added");

        var msg = new MSG();
        int ret;
        while (running && (ret = user32.GetMessage(msg, hWnd, 0, 0)) != 0) {
            if (ret == -1) {
                break; // GetMessage error
            }
            if (msg.message == WM_TRAY_CALLBACK) {
                handleTrayEvent(user32, hWnd, msg.lParam.intValue() & 0xFFFF);
            } else {
                user32.TranslateMessage(msg);
                user32.DispatchMessage(msg);
            }
        }
    }

    private void handleTrayEvent(User32 user32, HWND hWnd, int mouseMessage) {
        switch (mouseMessage) {
            case WM_LBUTTONUP, WM_LBUTTONDBLCLK -> eventBus.fire(new ShowMainEvent());
            case WM_RBUTTONUP -> showContextMenu(user32, hWnd);
            default -> { /* ignore moves / other mouse messages */ }
        }
    }

    private void showContextMenu(User32 user32, HWND hWnd) {
        var menu = WinUser32Ext.INSTANCE.CreatePopupMenu();
        if (menu == null) {
            return;
        }
        try {
            WinUser32Ext.INSTANCE.AppendMenuW(menu, WinUser32Ext.MF_STRING, MENU_EXIT, new WString("Exit"));
            var pt = new POINT();
            user32.GetCursorPos(pt);
            // Required so the menu dismisses correctly when the user clicks elsewhere.
            user32.SetForegroundWindow(hWnd);
            var cmd = WinUser32Ext.INSTANCE.TrackPopupMenu(menu,
                    WinUser32Ext.TPM_RETURNCMD | WinUser32Ext.TPM_RIGHTBUTTON, pt.x, pt.y, 0, hWnd, null);
            if (cmd == MENU_EXIT) {
                //noinspection CallToSystemExit
                System.exit(0);
            }
        } finally {
            WinUser32Ext.INSTANCE.DestroyMenu(menu);
        }
    }

    private HICON loadAppIcon() {
        try {
            var exe = ProcessHandle.current().info().command().orElse(null);
            if (exe != null) {
                var large = new HICON[1];
                var small = new HICON[1];
                if (Shell32.INSTANCE.ExtractIconEx(exe, 0, large, small, 1) > 0) {
                    if (small[0] != null) {
                        return small[0];
                    }
                    if (large[0] != null) {
                        return large[0];
                    }
                }
            }
        } catch (RuntimeException e) {
            log.trace("Unable to extract the application icon for the tray", e);
        }
        // Fall back to the generic application icon (IDI_APPLICATION via MAKEINTRESOURCE).
        return WinUser32Ext.INSTANCE.LoadIconW(null, new Pointer(IDI_APPLICATION));
    }
}
