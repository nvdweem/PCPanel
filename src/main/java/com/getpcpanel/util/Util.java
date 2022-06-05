package com.getpcpanel.util;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

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
    private static final Set<String> executables = Set.of("bat", "bin", "cmd", "com", "cpl", "exe", "gadget", "inf1", "ins", "inx", "isu", "job", "jse", "lnk", "msc", "msi", "msp", "mst", "paf",
            "pif", "ps1", "reg", "rgs", "scr", "sct", "shb", "shs", "u3p", "vb", "vbe", "vbs", "vbscript", "ws", "wsf", "wsh");

    private Util() {
    }

    public static String listToPipeDelimitedString(Collection<String> elements) {
        return String.join("|", elements);
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

    public static boolean isFileExecutable(File file) {
        return executables.contains(StringUtils.lowerCase(FilenameUtils.getExtension(file.getName())));
    }

    public static int map(int x, int in_min, int in_max, int out_min, int out_max) {
        return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
    }

    public static double map(double x, double in_min, double in_max, double out_min, double out_max) {
        return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
    }

    public static int analogValueToRotation(int x) {
        return 3 * x + 30;
    }

    public static void fill(Object[] ar, Object... objs) {
        System.arraycopy(objs, 0, ar, 0, objs.length);
    }
}
