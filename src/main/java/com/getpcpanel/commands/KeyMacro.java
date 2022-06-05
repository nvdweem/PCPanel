package com.getpcpanel.commands;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;

import lombok.extern.log4j.Log4j2;

@Log4j2
public final class KeyMacro {
    private static final Robot robot = buildRobot();

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
        try {
            if (input.contains("UNDEFINED"))
                return;
            var ar = input.replace(" ", "").split("\\+");
            for (var i = 0; i < ar.length - 1; i++) {
                var result = modifierToKeyEvent(ar[i]);
                if (result == 0) {
                    log.error("bad serialNum modifier: {}", ar[i]);
                } else {
                    robot.keyPress(result);
                }
            }
            var keyEvent = letterToKeyEvent(ar[ar.length - 1]);
            robot.keyPress(keyEvent);
            robot.keyRelease(keyEvent);
            for (var j = 0; j < ar.length - 1; j++) {
                var result = modifierToKeyEvent(ar[j]);
                if (result == 0) {
                    log.error("bad serialNum modifier: {}", ar[j]);
                } else {
                    robot.keyRelease(result);
                }
            }
        } catch (Exception e) {
            log.error("bad input: {}", input, e);
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
            default -> 0;
        };
    }
}
