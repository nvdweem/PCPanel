package com.getpcpanel.util;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.annotation.Nullable;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import lombok.extern.log4j.Log4j2;

@Log4j2
public final class Util {
    private static final Set<String> executables = Set.of("bat", "bin", "cmd", "com", "cpl", "exe", "gadget", "inf1", "ins", "inx", "isu", "job", "jse", "lnk", "msc", "msi", "msp", "mst", "paf",
            "pif", "ps1", "reg", "rgs", "scr", "sct", "shb", "shs", "u3p", "vb", "vbe", "vbs", "vbscript", "ws", "wsf", "wsh");

    private Util() {
    }

    /**
     * Format a CSS-style hex color string from RGB components (0-255 each).
     */
    @Nullable
    public static String formatHexString(int r, int g, int b) {
        return String.format("#%02x%02x%02x", r, g, b);
    }

    /**
     * Parse a CSS hex color string like "#rrggbb" into an int[]{r,g,b} array (0-255 each).
     * Returns null if the string is null or not parseable.
     */
    @Nullable
    public static int[] parseColorComponents(String color) {
        if (color == null)
            return null;
        try {
            String hex = color.startsWith("#") ? color.substring(1) : color;
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            return new int[] { r, g, b };
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parse a CSS hex color string and return it as-is (passthrough for backward compat).
     */
    public static Optional<String> parseColor(String color) {
        if (color == null)
            return Optional.empty();
        var components = parseColorComponents(color);
        if (components == null)
            return Optional.empty();
        return Optional.of(color);
    }

    public static List<Integer> numToList(int num) {
        return IntStream.rangeClosed(1, num).boxed().collect(Collectors.toList());
    }

    public static boolean isFileExecutable(File file) {
        return executables.contains(StringUtils.lowerCase(FilenameUtils.getExtension(file.getName())));
    }

    public static int map(int x, int in_min, int in_max, int out_min, int out_max) {
        var div = in_max - in_min;
        if (div == 0) {
            return 0;
        }
        return (x - in_min) * (out_max - out_min) / div + out_min;
    }

    public static double map(double x, double in_min, double in_max, double out_min, double out_max) {
        return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
    }

    public static float map(float x, float in_min, float in_max, float out_min, float out_max) {
        return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
    }

    public static double analogValueToRotation(int x) {
        return 3 * (x / 2.55f) + 30;
    }

    public static int rotationToAnalogValue(double x) {
        return (int) (((x - 30) / 3) * 2.55f);
    }

    public static void fill(Object[] ar, Object... objs) {
        System.arraycopy(objs, 0, ar, 0, objs.length);
    }
}
