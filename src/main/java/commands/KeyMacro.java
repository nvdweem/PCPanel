package commands;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;

import lombok.extern.log4j.Log4j2;

@Log4j2
public final class KeyMacro {
    private static final Robot robot;

    static {
        Robot toSet = null;
        try {
            toSet = new Robot();
        } catch (AWTException e) {
            log.error("Unable to construct robot", e);
        }
        robot = toSet;
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
                    log.error("bad key modifier: {}", ar[i]);
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
                    log.error("bad key modifier: {}", ar[j]);
                } else {
                    robot.keyRelease(result);
                }
            }
        } catch (Exception e) {
            log.error("bad input: {}", input, e);
        }
    }

    public static void main(String[] args) throws Exception {
        var s = System.currentTimeMillis();
        executeKeyStroke("ctrl + shift + alt + S");
        log.error("{}", (System.currentTimeMillis() - s) / 1000.0D);
    }

    private static int letterToKeyEvent(String letter) throws Exception {
        var ucLetter = letter.toUpperCase();
        var code = "VK_" + ucLetter;
        var f = KeyEvent.class.getField(code);
        return f.getInt(null);
    }

    private static int modifierToKeyEvent(String mod) {
        if ("ctrl".equals(mod))
            return 17;
        if ("shift".equals(mod))
            return 16;
        if ("alt".equals(mod))
            return 18;
        return 0;
    }
}
