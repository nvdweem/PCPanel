package commands;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;

public class KeyMacro {
    private static Robot robot;

    static {
        try {
            robot = new Robot();
        } catch (AWTException e) {
            e.printStackTrace();
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
                    System.err.println("bad key modifier: " + ar[i]);
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
                    System.err.println("bad key modifier: " + ar[j]);
                } else {
                    robot.keyRelease(result);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("bad input: " + input);
        }
    }

    public static void main(String[] args) throws Exception {
        long s = System.currentTimeMillis();
        executeKeyStroke("ctrl + shift + alt + S");
        System.err.println((System.currentTimeMillis() - s) / 1000.0D);
    }

    private static int letterToKeyEvent(String letter) throws Exception {
        letter = letter.toUpperCase();
        String code = "VK_" + letter;
        Field f = KeyEvent.class.getField(code);
        return f.getInt(null);
    }

    private static int modifierToKeyEvent(String mod) {
        if (mod.equals("ctrl"))
            return 17;
        if (mod.equals("shift"))
            return 16;
        if (mod.equals("alt"))
            return 18;
        return 0;
    }
}
