package com.getpcpanel.integration.program.platform.windows;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.integration.program.WindowFocuser;
import com.getpcpanel.platform.WindowsBuild;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.log4j.Log4j2;

/**
 * Raises the app's frontmost window: the first top-level window in Z-order that Alt+Tab would list (visible, not
 * cloaked, unowned, no tool window, with a title) and whose process runs the executable. The filter keeps an app's
 * hidden helper windows from being chosen. Windows only lets the foreground process hand out the foreground, so an Alt tap precedes
 * {@code SetForegroundWindow}; without it the call only flashes the taskbar button.
 *
 * <p>Top-level windows are walked with {@code GetWindow}, not {@code EnumWindows}: JNA callbacks do not run in the
 * native image.
 */
@Log4j2
@Unremovable
@WindowsBuild
@ApplicationScoped
class WinWindowFocuser implements WindowFocuser {
    private static final int WINDOW_WALK_LIMIT = 10_000;
    private static final int KEYEVENTF_KEYUP = 0x0002;
    private static final int WS_EX_TOOLWINDOW = 0x00000080;

    @Override
    public Result focusOrMinimize(String exe, boolean minimizeIfFocused) {
        var stem = WindowFocuser.appStem(exe);
        if (stem.isEmpty()) {
            return Result.NOT_RUNNING;
        }
        try {
            var window = findWindow(stem);
            if (window == null) {
                return Result.NOT_RUNNING;
            }
            var user32 = User32.INSTANCE;
            if (minimizeIfFocused && window.equals(user32.GetForegroundWindow())) {
                user32.ShowWindow(window, WinUser.SW_MINIMIZE);
                return Result.MINIMIZED;
            }
            if ((user32.GetWindowLong(window, WinUser.GWL_STYLE) & WinUser.WS_MINIMIZE) != 0) {
                user32.ShowWindow(window, WinUser.SW_RESTORE);
            }
            tapAlt();
            if (!user32.SetForegroundWindow(window)) {
                log.debug("SetForegroundWindow refused for {}", exe);
            }
            return Result.FOCUSED;
        } catch (RuntimeException e) {
            log.warn("Unable to bring {} to the front", exe, e);
            return Result.NOT_RUNNING;
        }
    }

    private static @Nullable WinDef.HWND findWindow(String stem) {
        var user32 = User32.INSTANCE;
        var exeByPid = new HashMap<Integer, String>();
        var pid = new IntByReference();
        var hWnd = user32.GetWindow(user32.GetDesktopWindow(), new WinDef.DWORD(WinUser.GW_CHILD));
        for (var guard = 0; hWnd != null && guard < WINDOW_WALK_LIMIT; guard++) {
            if (isAltTabWindow(hWnd)) {
                user32.GetWindowThreadProcessId(hWnd, pid);
                if (StringUtils.equalsIgnoreCase(stem, exeStem(exeByPid, pid.getValue()))) {
                    return hWnd;
                }
            }
            hWnd = user32.GetWindow(hWnd, new WinDef.DWORD(WinUser.GW_HWNDNEXT));
        }
        return null;
    }

    private static boolean isAltTabWindow(WinDef.HWND hWnd) {
        var user32 = User32.INSTANCE;
        return user32.IsWindowVisible(hWnd)
                && user32.GetWindow(hWnd, new WinDef.DWORD(WinUser.GW_OWNER)) == null
                && (user32.GetWindowLong(hWnd, WinUser.GWL_EXSTYLE) & WS_EX_TOOLWINDOW) == 0
                && user32.GetWindowTextLength(hWnd) > 0
                && !isCloaked(hWnd);
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

    private static void tapAlt() {
        sendAlt(0);
        sendAlt(KEYEVENTF_KEYUP);
    }

    private static void sendAlt(int flags) {
        var input = new WinUser.INPUT();
        input.type = new WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD);
        input.input.setType("ki");
        input.input.ki.wVk = new WinDef.WORD(WinUser.VK_MENU);
        input.input.ki.wScan = new WinDef.WORD(0);
        input.input.ki.time = new WinDef.DWORD(0);
        input.input.ki.dwExtraInfo = new BaseTSD.ULONG_PTR(0);
        input.input.ki.dwFlags = new WinDef.DWORD(flags);
        User32.INSTANCE.SendInput(new WinDef.DWORD(1), (WinUser.INPUT[]) input.toArray(1), input.size());
    }
}
