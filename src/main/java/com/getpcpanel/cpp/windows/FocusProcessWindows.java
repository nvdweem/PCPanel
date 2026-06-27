package com.getpcpanel.cpp.windows;

import java.util.OptionalInt;

import com.getpcpanel.cpp.FocusProcessService;
import com.getpcpanel.platform.WindowsBuild;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.ptr.IntByReference;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.log4j.Log4j2;

/** Foreground-window PID via Win32 {@code GetForegroundWindow} + {@code GetWindowThreadProcessId} (no SndCtrl.dll). */
@Log4j2
@ApplicationScoped
@WindowsBuild
public class FocusProcessWindows implements FocusProcessService {
    @Override
    public OptionalInt foregroundPid() {
        try {
            var hwnd = User32.INSTANCE.GetForegroundWindow();
            if (hwnd == null) {
                return OptionalInt.empty();
            }
            var pid = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(hwnd, pid);
            var value = pid.getValue();
            return value > 0 ? OptionalInt.of(value) : OptionalInt.empty();
        } catch (RuntimeException e) {
            log.debug("Foreground window PID lookup failed", e);
            return OptionalInt.empty();
        }
    }
}
