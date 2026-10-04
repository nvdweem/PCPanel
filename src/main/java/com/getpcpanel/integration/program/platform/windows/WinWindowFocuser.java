package com.getpcpanel.integration.program.platform.windows;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.integration.program.WindowFocuser;
import com.getpcpanel.platform.WindowsBuild;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.log4j.Log4j2;

/**
 * Raises the app's frontmost window: the first window {@link WinTopLevelWindows} lists whose process runs the
 * executable, which keeps an app's hidden helper windows from being chosen. Windows only lets the foreground process
 * hand out the foreground, so an Alt tap precedes {@code SetForegroundWindow}; without it the call only flashes the
 * taskbar button.
 */
@Log4j2
@Unremovable
@WindowsBuild
@ApplicationScoped
class WinWindowFocuser implements WindowFocuser {
    private static final int KEYEVENTF_KEYUP = 0x0002;

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
        return WinTopLevelWindows.list().stream()
                                 .filter(w -> StringUtils.equalsIgnoreCase(stem, w.exeStem()))
                                 .map(WinTopLevelWindows.Window::hwnd)
                                 .findFirst()
                                 .orElse(null);
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
