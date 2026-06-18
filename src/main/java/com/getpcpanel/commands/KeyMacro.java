package com.getpcpanel.commands;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.SystemUtils;

import com.getpcpanel.cpp.linux.LinuxKeyboard;
import com.getpcpanel.cpp.osx.OsxKeyboard;
import com.getpcpanel.cpp.windows.WindowsKeyboard;
import com.getpcpanel.util.OsxPermissionHelper;

import lombok.extern.log4j.Log4j2;

/**
 * Cross-platform keystroke injection facade. Each platform uses a native (JNA) backend instead of
 * {@link java.awt.Robot}, so the GraalVM native image never needs the AWT windowing toolkit:
 * macOS via CoreGraphics ({@link OsxKeyboard}), Windows via {@code User32.SendInput}
 * ({@link WindowsKeyboard}) and Linux via the X11 {@code XTEST} extension ({@link LinuxKeyboard}).
 */
@Log4j2
public final class KeyMacro {
    private static final AtomicBoolean accessibilityWarned = new AtomicBoolean();

    private KeyMacro() {
    }

    public static void executeKeyStroke(String input) {
        if (input == null || input.contains("UNDEFINED"))
            return;
        if (SystemUtils.IS_OS_MAC) {
            warnIfAccessibilityNotGranted();
            OsxKeyboard.executeKeyStroke(input);
        } else if (SystemUtils.IS_OS_WINDOWS) {
            WindowsKeyboard.executeKeyStroke(input);
        } else {
            LinuxKeyboard.executeKeyStroke(input);
        }
    }

    /** Types {@code text} out character-by-character via the platform's native keyboard backend. */
    public static void typeText(String text) {
        if (text == null || text.isEmpty())
            return;
        if (SystemUtils.IS_OS_MAC) {
            warnIfAccessibilityNotGranted();
            OsxKeyboard.typeText(text);
        } else if (SystemUtils.IS_OS_WINDOWS) {
            WindowsKeyboard.typeText(text);
        } else {
            LinuxKeyboard.typeText(text);
        }
    }

    private static void warnIfAccessibilityNotGranted() {
        if (SystemUtils.IS_OS_MAC && !OsxPermissionHelper.isAccessibilityGranted() && accessibilityWarned.compareAndSet(false, true)) {
            log.warn("Keystrokes require Accessibility permission: System Settings > Privacy & Security > Accessibility > enable PCPanel");
        }
    }
}
