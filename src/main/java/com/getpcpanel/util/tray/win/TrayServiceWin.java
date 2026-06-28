package com.getpcpanel.util.tray.win;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.getpcpanel.platform.WindowsBuild;
import com.getpcpanel.util.io.FileUtil;
import com.getpcpanel.util.app.OpenFolderEvent;
import com.getpcpanel.util.app.ShowMainEvent;
import com.getpcpanel.util.tray.ITrayService;
import com.getpcpanel.util.tray.awt.AwtTrayImpl;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HICON;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.MSG;
import com.sun.jna.platform.win32.WinUser.WNDCLASSEX;
import com.sun.jna.platform.win32.WinUser.WindowProc;

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
 * <p>The notification-area callback ({@code WM_TRAY_CALLBACK}) is delivered by the shell to the
 * <b>window procedure</b> of the icon's window, not posted as a plain queue message, so a real
 * window procedure is required to receive clicks — a message-loop "intercept" of the built-in
 * {@code "STATIC"} class never sees them. A JNA {@link WindowProc} callback is used for this, exactly
 * like {@link com.getpcpanel.sleepdetection.WindowsSystemEventService}; such native&#8594;Java upcalls
 * work in the current GraalVM native image. The whole thing is wrapped so a tray failure can never
 * take down the app — a missing tray icon is acceptable degradation.
 */
@Log4j2
@ApplicationScoped
@WindowsBuild
@AwtTrayImpl
public class TrayServiceWin implements ITrayService, WindowProc {
    private static final String WINDOW_CLASS = "PCPanelTrayWindow";
    private static final String ICON_RESOURCE = "/assets/app-icon.ico";
    private static final int WM_TRAY_CALLBACK = WinUser.WM_USER + 1;
    private static final int TRAY_ID = 1;
    private static final int MENU_OPEN = 1;
    private static final int MENU_EXIT = 2;
    private static final int MENU_SETTINGS = 3;
    private static final int IDI_APPLICATION = 32512;

    // Mouse messages delivered in the low word of the callback's lParam (NOTIFYICON_VERSION 0).
    private static final int WM_LBUTTONUP = 0x0202;
    private static final int WM_LBUTTONDBLCLK = 0x0203;
    private static final int WM_RBUTTONUP = 0x0205;
    private static final int WM_CONTEXTMENU = 0x007B;
    private static final int WM_NULL = 0x0000;

    @Inject Event<Object> eventBus;
    @Inject FileUtil fileUtil;

    private volatile WinShell32.NOTIFYICONDATA nid;
    private volatile int taskbarCreatedMsg;

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

        var wClass = new WNDCLASSEX();
        wClass.hInstance = hInst;
        wClass.lpfnWndProc = this; // JNA WindowProc upcall; receives the tray notifications
        wClass.lpszClassName = WINDOW_CLASS;
        if (user32.RegisterClassEx(wClass).intValue() == 0) {
            log.warn("Unable to register tray window class (error {})", Kernel32.INSTANCE.GetLastError());
            return;
        }

        var hWnd = user32.CreateWindowEx(0, WINDOW_CLASS, "PCPanel Tray", 0, 0, 0, 0, 0,
                null, null, hInst, null);
        if (hWnd == null) {
            log.warn("Unable to create tray window (error {})", Kernel32.INSTANCE.GetLastError());
            return;
        }

        // Re-add the icon when Explorer restarts (it broadcasts "TaskbarCreated").
        taskbarCreatedMsg = WinUser32Ext.INSTANCE.RegisterWindowMessageW(new WString("TaskbarCreated"));

        nid = new WinShell32.NOTIFYICONDATA();
        nid.hWnd = hWnd;
        nid.uID = TRAY_ID;
        nid.uFlags = WinShell32.NIF_ICON | WinShell32.NIF_MESSAGE | WinShell32.NIF_TIP;
        nid.uCallbackMessage = WM_TRAY_CALLBACK;
        nid.hIcon = loadAppIcon(user32);
        nid.setTip("PCPanel");

        if (!WinShell32.INSTANCE.Shell_NotifyIcon(WinShell32.NIM_ADD, nid)) {
            log.warn("Shell_NotifyIcon(NIM_ADD) failed (error {})", Kernel32.INSTANCE.GetLastError());
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> WinShell32.INSTANCE.Shell_NotifyIcon(WinShell32.NIM_DELETE, nid), "PCPanel Tray cleanup"));
        log.debug("Windows tray icon added");

        var msg = new MSG();
        while (user32.GetMessage(msg, hWnd, 0, 0) != 0) {
            user32.TranslateMessage(msg);
            user32.DispatchMessage(msg);
        }
    }

    /**
     * Window procedure (JNA upcall). The shell delivers tray notifications here; the menu uses
     * {@code TrackPopupMenu(TPM_RETURNCMD)} so the chosen command is handled inline.
     */
    @Override
    public LRESULT callback(HWND hWnd, int uMsg, WPARAM wParam, LPARAM lParam) {
        if (uMsg == WM_TRAY_CALLBACK) {
            handleTrayEvent(hWnd, lParam.intValue() & 0xFFFF);
            return new LRESULT(0);
        }
        if (uMsg == taskbarCreatedMsg && nid != null) {
            WinShell32.INSTANCE.Shell_NotifyIcon(WinShell32.NIM_ADD, nid);
            return new LRESULT(0);
        }
        if (uMsg == WinUser.WM_DESTROY) {
            User32.INSTANCE.PostQuitMessage(0);
            return new LRESULT(0);
        }
        return User32.INSTANCE.DefWindowProc(hWnd, uMsg, wParam, lParam);
    }

    private void handleTrayEvent(HWND hWnd, int mouseMessage) {
        switch (mouseMessage) {
            case WM_LBUTTONUP, WM_LBUTTONDBLCLK -> eventBus.fire(new ShowMainEvent());
            case WM_RBUTTONUP, WM_CONTEXTMENU -> showContextMenu(hWnd);
            default -> { /* ignore moves / other mouse messages */ }
        }
    }

    private void showContextMenu(HWND hWnd) {
        var user32 = User32.INSTANCE;
        var ext = WinUser32Ext.INSTANCE;
        var menu = ext.CreatePopupMenu();
        if (menu == null) {
            return;
        }
        try {
            ext.AppendMenuW(menu, WinUser32Ext.MF_STRING, MENU_OPEN, new WString("Open PCPanel"));
            ext.AppendMenuW(menu, WinUser32Ext.MF_STRING, MENU_SETTINGS, new WString("Open settings folder"));
            ext.AppendMenuW(menu, WinUser32Ext.MF_STRING, MENU_EXIT, new WString("Exit"));
            ext.SetMenuDefaultItem(menu, MENU_OPEN, 0);

            var pt = new POINT();
            user32.GetCursorPos(pt);
            // Required so the menu dismisses correctly when the user clicks elsewhere; the trailing
            // WM_NULL is the documented workaround for a tray menu that otherwise sticks around.
            user32.SetForegroundWindow(hWnd);
            var cmd = ext.TrackPopupMenu(menu,
                    WinUser32Ext.TPM_RETURNCMD | WinUser32Ext.TPM_RIGHTBUTTON, pt.x, pt.y, 0, hWnd, null);
            user32.PostMessage(hWnd, WM_NULL, new WPARAM(0), new LPARAM(0));
            switch (cmd) {
                case MENU_OPEN -> eventBus.fire(new ShowMainEvent());
                case MENU_SETTINGS -> eventBus.fire(new OpenFolderEvent(fileUtil.getRoot().toString()));
                case MENU_EXIT -> {
                    //noinspection CallToSystemExit
                    System.exit(0);
                }
                default -> { /* menu dismissed */ }
            }
        } finally {
            ext.DestroyMenu(menu);
        }
    }

    /**
     * Loads the PCPanel icon for the tray. Prefers the bundled multi-size {@code app-icon.ico} (parsed
     * to the entry closest to the small-icon size, so it is crisp), then the executable's own icon, and
     * finally the generic application icon.
     */
    private HICON loadAppIcon(User32 user32) {
        var desired = user32.GetSystemMetrics(WinUser.SM_CXSMICON);
        if (desired <= 0) {
            desired = 16;
        }
        var fromResource = loadIconFromResource(desired);
        if (fromResource != null) {
            return fromResource;
        }
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
        return WinUser32Ext.INSTANCE.LoadIconW(null, new Pointer(IDI_APPLICATION));
    }

    /**
     * Parses the bundled {@code .ico} and builds an {@link HICON} from the directory entry whose size is
     * closest to {@code desired}, via {@code CreateIconFromResourceEx}. Returns {@code null} on any
     * problem so the caller can fall back.
     */
    private HICON loadIconFromResource(int desired) {
        try (InputStream in = TrayServiceWin.class.getResourceAsStream(ICON_RESOURCE)) {
            if (in == null) {
                return null;
            }
            var ico = in.readAllBytes();
            var bb = ByteBuffer.wrap(ico).order(ByteOrder.LITTLE_ENDIAN);
            var count = bb.getShort(4) & 0xFFFF;
            if (count <= 0 || ico.length < 6 + count * 16) {
                return null;
            }
            var bestOffset = -1;
            var bestSize = 0;
            var bestWidth = 0;
            var bestScore = Integer.MAX_VALUE;
            for (var i = 0; i < count; i++) {
                var off = 6 + i * 16;
                var w = ico[off] & 0xFF;
                if (w == 0) {
                    w = 256;
                }
                var bytesInRes = bb.getInt(off + 8);
                var imageOffset = bb.getInt(off + 12);
                if (imageOffset < 0 || bytesInRes <= 0 || imageOffset + bytesInRes > ico.length) {
                    continue;
                }
                var score = Math.abs(w - desired);
                if (score < bestScore) {
                    bestScore = score;
                    bestOffset = imageOffset;
                    bestSize = bytesInRes;
                    bestWidth = w;
                }
            }
            if (bestOffset < 0) {
                return null;
            }
            try (var mem = new Memory(bestSize)) {
                mem.write(0, ico, bestOffset, bestSize);
                var icon = WinUser32Ext.INSTANCE.CreateIconFromResourceEx(mem, bestSize, true, 0x00030000,
                        bestWidth, bestWidth, WinUser32Ext.LR_DEFAULTCOLOR);
                if (icon == null) {
                    log.trace("CreateIconFromResourceEx failed (error {})", Kernel32.INSTANCE.GetLastError());
                }
                return icon;
            }
        } catch (IOException | RuntimeException e) {
            log.trace("Unable to load the bundled tray icon", e);
            return null;
        }
    }
}
