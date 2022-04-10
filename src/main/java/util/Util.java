package util;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FilenameUtils;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

public class Util {
    public static String listToPipeDelimitedString(String... elements) {
        return String.join("|", elements);
    }

    public static String listToPipeDelimitedString(Collection<String> elements) {
        return String.join("|", elements);
    }

    public static boolean isNullOrEmpty(String str) {
        return !(str != null && str.length() != 0);
    }

    public static void adjustTabs(TabPane tabPane, int width, int height) {
        adjustTabs(tabPane);
        tabPane.setTabMinHeight(width);
        tabPane.setTabMaxHeight(width);
        tabPane.setTabMinWidth(height);
    }

    public static void adjustTabs(TabPane tabPane) {
        tabPane.setRotateGraphic(true);
        for (Tab tab : tabPane.getTabs()) {
            Label l = new Label(tab.getText());
            l.setPadding(new Insets(0.0D, 0.0D, 0.0D, 10.0D));
            l.setRotate(90.0D);
            StackPane stp = new StackPane(new Group(l));
            stp.setAlignment(Pos.TOP_CENTER);
            stp.setPrefHeight(200.0D);
            stp.setRotate(90.0D);
            tab.setGraphic(stp);
            tab.setText("");
        }
    }

    public static String formatHexString(Color c) {
        if (c != null)
            return String.format(null, "#%02x%02x%02x", new Object[] { Long.valueOf(Math.round(c.getRed() * 255.0D)),
                    Long.valueOf(Math.round(c.getGreen() * 255.0D)),
                    Long.valueOf(Math.round(c.getBlue() * 255.0D)) });
        return null;
    }

    public static int toInt(String str, int defaultVal) {
        try {
            return Integer.parseInt(str);
        } catch (Exception e) {
            return defaultVal;
        }
    }

    public static void printByteArray(byte[] array) {
        byte b;
        int i;
        byte[] arrayOfByte;
        for (i = (arrayOfByte = array).length, b = 0; b < i; ) {
            byte b1 = arrayOfByte[b];
            System.out.printf("%02X\t", Byte.valueOf(b1));
            b++;
        }
        System.out.println();
    }

    public static List<Integer> numToList(int num) {
        List<Integer> ret = new ArrayList<>();
        for (int i = 1; i <= num; ) {
            ret.add(Integer.valueOf(i));
            i++;
        }
        return ret;
    }

    public static <T> void changeItemsTo(ChoiceBox<T> cb, List<T> list) {
        changeItemsTo(cb, list, true);
    }

    public static <T> void changeItemsTo(ChoiceBox<T> cb, List<T> list, boolean nevernull) {
        T prev = cb.getValue();
        cb.getItems().setAll(list);
        if (list.contains(prev)) {
            cb.setValue(prev);
        } else if (nevernull) {
            cb.getSelectionModel().selectFirst();
        } else {
            cb.setValue(null);
        }
    }

    public static void clearAndSetNull(ChoiceBox<?> cb) {
        cb.setValue(null);
        cb.getItems().clear();
    }

    public static boolean matches(String str, String comp) {
        if (str.length() == 0)
            return false;
        char curStr = Character.MIN_VALUE;
        int strIndex = 0;
        boolean skipPart = false;
        for (int i = 0; i < comp.length(); i++) {
            if (!skipPart)
                curStr = str.charAt(strIndex);
            char curComp = comp.charAt(i);
            if (!skipPart || curComp == '|')
                if (curComp == '|') {
                    skipPart = false;
                    strIndex = 0;
                } else if (curComp == curStr) {
                    if (strIndex + 1 == str.length()) {
                        if (i + 1 == comp.length() || comp.charAt(i + 1) == '|')
                            return true;
                        skipPart = true;
                    } else {
                        strIndex++;
                    }
                } else {
                    skipPart = true;
                }
        }
        return false;
    }

    public static boolean isFileExecutable(File file) {
        String ext = FilenameUtils.getExtension(file.getName());
        return matches(ext, "bat|bin|cmd|com|cpl|exe|gadget|inf1|ins|inx|isu|job|jse|lnk|msc|msi|msp|mst|paf|pif|ps1|reg|rgs|scr|sct|shb|shs|u3p|vb|vbe|vbs|vbscript|ws|wsf|wsh");
    }

    public static int map(int x, int in_min, int in_max, int out_min, int out_max) {
        return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
    }

    public static double map(double x, double in_min, double in_max, double out_min, double out_max) {
        return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
    }

    public static void main(String[] args) {
        for (int i = 0; i <= 100; i++) {
            int tech = percentToBytePreserve(i);
            int back = map(tech, 0, 255, 0, 100);
            System.out.println(i + "\t" + tech + "\t" + back);
            if (i != back)
                System.err.println("ERROR: " + i + "\t" + tech + "\t" + back);
        }
    }

    public static int percentToBytePreserve(int percent) {
        int ret = map(percent, 0, 100, 0, 255);
        return (ret == 0 || ret == 255) ? ret : (ret + 1);
    }

    public static int analogValueToRotation(int x) {
        return 3 * x + 30;
    }

    @SafeVarargs
    public static <T> void fill(Object[] ar, Object... objs) {
        for (int i = 0; i < objs.length; ) {
            ar[i] = objs[i];
            i++;
        }
    }
}

