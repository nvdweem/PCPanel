package com.getpcpanel.commands;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.SystemUtils;

import com.getpcpanel.cpp.osx.OsxKeyboard;
import com.getpcpanel.util.OsxPermissionHelper;

import lombok.extern.log4j.Log4j2;

@Log4j2
public final class KeyMacro {
    // On macOS there is no AWT/libawt in the native image, so Robot cannot be constructed there;
    // keystrokes go through CoreGraphics CGEvents (OsxKeyboard) instead.
    private static final Robot robot = SystemUtils.IS_OS_MAC ? null : buildRobot();
    private static final AtomicBoolean accessibilityWarned = new AtomicBoolean();

    private static Robot buildRobot() {
        try {
            return new Robot();
        } catch (AWTException | RuntimeException | LinkageError e) {
            log.error("Unable to construct robot", e);
        }
        return null;
    }

    private KeyMacro() {
    }

    public static void executeKeyStroke(String input) {
        if (input.contains("UNDEFINED"))
            return;
        if (SystemUtils.IS_OS_MAC) {
            warnIfAccessibilityNotGranted();
            OsxKeyboard.executeKeyStroke(input);
            return;
        }
        var ar = input.replace(" ", "").split("\\+");
        // Track what we actually pressed so the finally block can release exactly those, in reverse order.
        // Releasing on failure too prevents a half-finished combination from leaving a modifier stuck system-wide.
        var pressed = new ArrayList<Integer>();
        try {
            for (var i = 0; i < ar.length - 1; i++) {
                var result = modifierToKeyEvent(ar[i]);
                if (result == 0) {
                    log.error("bad serialNum modifier: {}", ar[i]);
                } else {
                    robot.keyPress(result);
                    pressed.add(result);
                }
            }
            var keyEvent = letterToKeyEvent(ar[ar.length - 1]);
            robot.keyPress(keyEvent);
            pressed.add(keyEvent);
        } catch (Exception e) {
            log.error("bad input: {}", input, e);
        } finally {
            for (var i = pressed.size() - 1; i >= 0; i--) {
                try {
                    robot.keyRelease(pressed.get(i));
                } catch (Exception e) {
                    log.error("Unable to release key {}", pressed.get(i), e);
                }
            }
        }
    }

    private static void warnIfAccessibilityNotGranted() {
        if (SystemUtils.IS_OS_MAC && !OsxPermissionHelper.isAccessibilityGranted() && accessibilityWarned.compareAndSet(false, true)) {
            log.warn("Keystrokes require Accessibility permission: System Settings > Privacy & Security > Accessibility > enable PCPanel");
        }
    }

    private static int letterToKeyEvent(String letter) throws Exception {
        var ucLetter = letter.toUpperCase();
        var code = "VK_" + ucLetter;
        var f = KeyEvent.class.getField(code);
        return f.getInt(null);
    }

    private static int modifierToKeyEvent(String mod) {
        return switch (mod) {
            case "ctrl" -> KeyEvent.VK_CONTROL;
            case "shift" -> KeyEvent.VK_SHIFT;
            case "alt" -> KeyEvent.VK_ALT;
            case "cmd", "command" -> KeyEvent.VK_META;
            case "windows" -> KeyEvent.VK_WINDOWS;
            default -> 0;
        };
    }
}
