package commands;

import lombok.extern.log4j.Log4j2;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;

@Log4j2
public class KeyMacro {
    private static Robot robot;

    static {
        try {
            robot = new Robot();
        } catch (AWTException e) {
            log.error("Unable to construct robot", e);
        }
    }

    public static void executeKeyStroke(String input) {
        try {
            if (input.contains("UNDEFINED"))
                return;
            String[] ar = input.replace(" ", "").split("\\+");
            for (int i = 0; i < ar.length - 1; i++) {
                int result = modifierToKeyEvent(ar[i]);
                if (result == 0) {
                    log.error("bad key modifier: {}", ar[i]);
                } else {
                    robot.keyPress(result);
                }
            }
            int keyEvent = letterToKeyEvent(ar[ar.length - 1]);
            robot.keyPress(keyEvent);
            robot.keyRelease(keyEvent);
            for (int j = 0; j < ar.length - 1; j++) {
                int result = modifierToKeyEvent(ar[j]);
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
        long s = System.currentTimeMillis();
        executeKeyStroke("ctrl + shift + alt + S");
        log.error("{}", (System.currentTimeMillis() - s) / 1000.0D);
    }

    private static int letterToKeyEvent(String letter) throws Exception {
        letter = letter.toUpperCase();
        String code = "VK_" + letter;
        Field f = KeyEvent.class.getField(code);
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
