package com.getpcpanel.commands;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.SystemUtils;

import com.getpcpanel.util.OsxPermissionHelper;

import lombok.extern.log4j.Log4j2;

@Log4j2
public final class KeyMacro {
    private static final Robot robot = buildRobot();
    private static final AtomicBoolean accessibilityWarned = new AtomicBoolean();

    private static Robot buildRobot() {
        try {
            return new Robot();
        } catch (AWTException e) {
            log.error("Unable to construct robot", e);
        }
        return null;
    }

    private KeyMacro() {
    }

    public static void executeKeyStroke(String input) {
        var pressed = new ArrayList<Integer>();
        try {
            if (input.contains("UNDEFINED"))
                return;
            warnIfAccessibilityNotGranted();
            var ar = input.replace(" ", "").split("\\+");
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
            for (var i = pressed.size() - 1; i >= 0; i--) { // Release everything that was pressed, otherwise modifiers stay pressed system-wide
                try {
                    robot.keyRelease(pressed.get(i));
                } catch (Exception e) {
                    log.error("Unable to release key {}", pressed.get(i), e);
                }
            }
        }
    }

    private static void warnIfAccessibilityNotGranted() {
        if (SystemUtils.IS_OS_MAC && !OsxPermissionHelper.isAccessibilityGranted() && !accessibilityWarned.getAndSet(true)) {
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
