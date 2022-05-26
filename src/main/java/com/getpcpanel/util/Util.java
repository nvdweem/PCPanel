package com.getpcpanel.util;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import lombok.extern.log4j.Log4j2;

@Log4j2
public final class Util {
    public static final File sndCtrlExecutable = extractAndDeleteOnExit("sndctrl.exe");

    private Util() {
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
        for (var tab : tabPane.getTabs()) {
            var l = new Label(tab.getText());
            l.setPadding(new Insets(0.0D, 0.0D, 0.0D, 10.0D));
            l.setRotate(90.0D);
            var stp = new StackPane(new Group(l));
            stp.setAlignment(Pos.TOP_CENTER);
            stp.setPrefHeight(200.0D);
            stp.setRotate(90.0D);
            tab.setGraphic(stp);
            tab.setText("");
        }
    }

    public static String formatHexString(Color c) {
        if (c != null)
            return String.format(null, "#%02x%02x%02x", new Object[] { Math.round(c.getRed() * 255.0D),
                    Math.round(c.getGreen() * 255.0D),
                    Math.round(c.getBlue() * 255.0D) });
        return null;
    }

    public static int toInt(String str, int defaultVal) {
        try {
            return Integer.parseInt(str);
        } catch (Exception e) {
            return defaultVal;
        }
    }

    public static List<Integer> numToList(int num) {
        return IntStream.rangeClosed(1, num).boxed().collect(Collectors.toList());
    }

    public static <T> void changeItemsTo(ChoiceBox<T> cb, List<T> list) {
        changeItemsTo(cb, list, true);
    }

    public static <T> void changeItemsTo(ChoiceBox<T> cb, List<T> list, boolean nevernull) {
        var prev = cb.getValue();
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
        var curStr = Character.MIN_VALUE;
        var strIndex = 0;
        var skipPart = false;
        for (var i = 0; i < comp.length(); i++) {
            if (!skipPart)
                curStr = str.charAt(strIndex);
            var curComp = comp.charAt(i);
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
        var ext = FilenameUtils.getExtension(file.getName());
        return matches(ext, "bat|bin|cmd|com|cpl|exe|gadget|inf1|ins|inx|isu|job|jse|lnk|msc|msi|msp|mst|paf|pif|ps1|reg|rgs|scr|sct|shb|shs|u3p|vb|vbe|vbs|vbscript|ws|wsf|wsh");
    }

    public static int map(int x, int in_min, int in_max, int out_min, int out_max) {
        return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
    }

    public static double map(double x, double in_min, double in_max, double out_min, double out_max) {
        return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
    }

    public static void main(String[] args) {
        for (var i = 0; i <= 100; i++) {
            var tech = percentToBytePreserve(i);
            var back = map(tech, 0, 255, 0, 100);
            log.info("{}\t{}\t{}", i, tech, back);
            if (i != back)
                log.error("ERROR: {}\t{}\t{}", i, tech, back);
        }
    }

    public static int percentToBytePreserve(int percent) {
        var ret = map(percent, 0, 100, 0, 255);
        return (ret == 0 || ret == 255) ? ret : (ret + 1);
    }

    public static int analogValueToRotation(int x) {
        return 3 * x + 30;
    }

    public static void fill(Object[] ar, Object... objs) {
        System.arraycopy(objs, 0, ar, 0, objs.length);
    }

    public static File extractAndDeleteOnExit(String file) {
        var extracted = new File(System.getProperty("java.io.tmpdir"), file);
        if (extracted.exists() && !extracted.delete()) {
            log.info("{} already exists, not updating", extracted);
            return extracted;
        }

        try {
            var resource = Util.class.getResource("/" + file);
            FileUtils.copyURLToFile(resource, extracted);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        extracted.deleteOnExit();
        return extracted;
    }
}
