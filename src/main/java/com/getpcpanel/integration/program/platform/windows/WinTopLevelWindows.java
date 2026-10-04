package com.getpcpanel.integration.program.platform.windows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.getpcpanel.integration.program.WindowFocuser;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;

/**
 * The top-level windows Alt+Tab would list (visible, unowned, no tool window, with a title), in Z-order, each with the
 * executable its process runs. The filter leaves out an app's hidden helper windows. A cloaked window (on another
 * virtual desktop, or a suspended Store app) is visible to {@code IsWindowVisible} but not on screen; whether it is
 * listed is up to the caller.
 *
 * <p>Windows are walked with {@code GetWindow}, not {@code EnumWindows}: JNA callbacks do not run in the native image.
 */
public final class WinTopLevelWindows {
    private static final int WINDOW_WALK_LIMIT = 10_000;
    private static final int WS_EX_TOOLWINDOW = 0x00000080;

    /** A listed window and its program's file name without path or {@code .exe}, as the file system spells it. */
    public record Window(WinDef.HWND hwnd, String exeStem) {
        /** The window's title as it is now; empty when it has none or is gone. */
        public String title() {
            var user32 = User32.INSTANCE;
            var length = user32.GetWindowTextLength(hwnd);
            if (length <= 0) {
                return "";
            }
            var buffer = new char[length + 1];
            var copied = user32.GetWindowText(hwnd, buffer, buffer.length);
            return copied <= 0 ? "" : new String(buffer, 0, copied);
        }
    }

    private WinTopLevelWindows() {
    }

    /** The windows Alt+Tab would list on this virtual desktop, frontmost first. */
    public static List<Window> list() {
        return list(false);
    }

    /** {@link #list()}, with {@code includeCloaked} also listing cloaked windows such as those on other virtual desktops. */
    public static List<Window> list(boolean includeCloaked) {
        var user32 = User32.INSTANCE;
        var result = new ArrayList<Window>();
        var exeByPid = new HashMap<Integer, String>();
        var pid = new IntByReference();
        var hWnd = user32.GetWindow(user32.GetDesktopWindow(), new WinDef.DWORD(WinUser.GW_CHILD));
        for (var guard = 0; hWnd != null && guard < WINDOW_WALK_LIMIT; guard++) {
            if (isAltTabWindow(hWnd) && (includeCloaked || !isCloaked(hWnd))) {
                user32.GetWindowThreadProcessId(hWnd, pid);
                result.add(new Window(hWnd, exeStem(exeByPid, pid.getValue())));
            }
            hWnd = user32.GetWindow(hWnd, new WinDef.DWORD(WinUser.GW_HWNDNEXT));
        }
        return result;
    }

    private static boolean isAltTabWindow(WinDef.HWND hWnd) {
        var user32 = User32.INSTANCE;
        return user32.IsWindowVisible(hWnd)
                && user32.GetWindow(hWnd, new WinDef.DWORD(WinUser.GW_OWNER)) == null
                && (user32.GetWindowLong(hWnd, WinUser.GWL_EXSTYLE) & WS_EX_TOOLWINDOW) == 0
                && user32.GetWindowTextLength(hWnd) > 0;
    }

    private static boolean isCloaked(WinDef.HWND hWnd) {
        var cloaked = new IntByReference();
        return WinDwmapi.INSTANCE.DwmGetWindowAttribute(hWnd, WinDwmapi.DWMWA_CLOAKED, cloaked, Integer.BYTES) == 0 && cloaked.getValue() != 0;
    }

    private static String exeStem(Map<Integer, String> cache, int pid) {
        return cache.computeIfAbsent(pid, p -> {
            var path = imagePath(p);
            return path == null ? "" : WindowFocuser.appStem(path);
        });
    }

    private static @Nullable String imagePath(int pid) {
        var process = Kernel32.INSTANCE.OpenProcess(WinNT.PROCESS_QUERY_LIMITED_INFORMATION, false, pid);
        if (process == null) {
            return null;
        }
        try {
            var buffer = new char[WinDef.MAX_PATH * 4];
            var size = new IntByReference(buffer.length);
            return Kernel32.INSTANCE.QueryFullProcessImageName(process, 0, buffer, size) ? new String(buffer, 0, size.getValue()) : null;
        } finally {
            Kernel32.INSTANCE.CloseHandle(process);
        }
    }
}
