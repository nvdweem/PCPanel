package com.getpcpanel.alerts.platform.windows;

import java.nio.file.Path;

import javax.annotation.Nullable;

import com.getpcpanel.alerts.TaskbarFlashEvent;
import com.getpcpanel.platform.WindowsBuild;
import com.getpcpanel.util.tray.win.WinUser32Ext;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HINSTANCE;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser.MSG;
import com.sun.jna.platform.win32.WinUser.WNDCLASSEX;
import com.sun.jna.platform.win32.WinUser.WindowProc;
import com.sun.jna.ptr.IntByReference;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * Reports apps flashing their taskbar button, the signal chat apps (Discord, Teams, Slack, ...) give for a new
 * message. A hidden top-level window registered with {@code RegisterShellHookWindow} receives {@code HSHELL_FLASH}
 * with the flashing window; shell hooks need a real top-level window, not a message-only one.
 */
@Log4j2
@Startup
@WindowsBuild
@ApplicationScoped
public class WindowsShellHookMonitor {
    private static final String WINDOW_CLASS = "PcPanelShellHook";
    private static final int WM_CLOSE = 0x0010;
    private static final int WM_DESTROY = 0x0002;
    /** {@code HSHELL_REDRAW | HSHELL_HIGHBIT}: a window flashed its taskbar button. */
    private static final int HSHELL_FLASH = 0x8006;

    @Inject Event<Object> eventBus;

    @Nullable private volatile HWND hwnd;
    private int shellHookMessage;
    @Nullable private Thread thread;
    @Nullable private WindowProc wndProc; // held so the callback is not collected while the window lives

    @PostConstruct
    void start() {
        thread = new Thread(this::run, "windows-shell-hook");
        thread.setDaemon(true);
        thread.start();
    }

    @PreDestroy
    void stop() {
        var h = hwnd;
        if (h != null) {
            User32.INSTANCE.PostMessage(h, WM_CLOSE, new WPARAM(0), new LPARAM(0));
        }
    }

    private void run() {
        try {
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
            var h = User32.INSTANCE.CreateWindowEx(0, WINDOW_CLASS, WINDOW_CLASS, 0, 0, 0, 0, 0, null, null, hInst, null);
            if (h == null) {
                throw new IllegalStateException("CreateWindowEx failed (err=" + Kernel32.INSTANCE.GetLastError() + ")");
            }
            hwnd = h;
            shellHookMessage = WinUser32Ext.INSTANCE.RegisterWindowMessageW(new WString("SHELLHOOK"));
            if (!WinUser32Ext.INSTANCE.RegisterShellHookWindow(h)) {
                throw new IllegalStateException("RegisterShellHookWindow failed (err=" + Kernel32.INSTANCE.GetLastError() + ")");
            }
            log.info("Taskbar-flash detection started");
            var msg = new MSG();
            while (User32.INSTANCE.GetMessage(msg, h, 0, 0) > 0) {
                User32.INSTANCE.TranslateMessage(msg);
                User32.INSTANCE.DispatchMessage(msg);
            }
        } catch (Throwable e) { // NOSONAR - alerts are non-essential and must never crash the app
            log.warn("Taskbar-flash detection unavailable: {}", e.toString());
        }
    }

    private LRESULT windowProc(HWND hWnd, int uMsg, WPARAM wParam, LPARAM lParam) {
        if (uMsg == shellHookMessage && shellHookMessage != 0) {
            if (wParam.intValue() == HSHELL_FLASH) {
                exeOf(new HWND(new Pointer(lParam.longValue()))).ifPresent(exe -> eventBus.fire(new TaskbarFlashEvent(exe)));
            }
            return new LRESULT(0);
        }
        switch (uMsg) {
            case WM_CLOSE -> {
                WinUser32Ext.INSTANCE.DeregisterShellHookWindow(hWnd);
                User32.INSTANCE.DestroyWindow(hWnd);
                return new LRESULT(0);
            }
            case WM_DESTROY -> {
                User32.INSTANCE.PostQuitMessage(0);
                return new LRESULT(0);
            }
            default -> {
                return User32.INSTANCE.DefWindowProc(hWnd, uMsg, wParam, lParam);
            }
        }
    }

    private static java.util.Optional<String> exeOf(HWND window) {
        try {
            var pid = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(window, pid);
            return ProcessHandle.of(pid.getValue())
                                .flatMap(p -> p.info().command())
                                .map(cmd -> Path.of(cmd).getFileName().toString());
        } catch (RuntimeException e) {
            log.debug("Unable to resolve the flashing window's app", e);
            return java.util.Optional.empty();
        }
    }
}
