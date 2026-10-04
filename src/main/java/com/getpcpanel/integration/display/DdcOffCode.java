package com.getpcpanel.integration.display;

import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

/**
 * The DDC/CI power-mode value (VCP code {@code D6}) that turns a monitor off. {@code 04} (off, DPM) is what monitors
 * with the power-mode code take, and it keeps the monitor on the cable so {@code 01} turns it back on. {@code 05}
 * switches it off as its power button would; some monitors list it and ignore it (a Samsung does), so it is used only
 * by a monitor whose capabilities string lists no {@code 04}.
 */
public final class DdcOffCode {
    public static final int ON = 1;
    public static final int STANDBY_OFF = 4;
    public static final int POWER_OFF = 5;

    private DdcOffCode() {
    }

    /**
     * {@link #POWER_OFF} when the {@code D6(...)} value list in the raw {@code vcp(...)} section of {@code capabilities}
     * names {@code 05} and no {@code 04}, else {@link #STANDBY_OFF}. Text around the raw string, such as the feature list
     * {@code ddcutil capabilities --verbose} prints above it, is ignored.
     */
    public static int choose(@Nullable String capabilities) {
        if (capabilities == null) {
            return STANDBY_OFF;
        }
        var caps = vcpSection(capabilities.toUpperCase(Locale.ROOT));
        for (var at = caps.indexOf("D6"); at >= 0; at = caps.indexOf("D6", at + 2)) {
            if (at > 0 && isHex(caps.charAt(at - 1))) {
                continue; // Part of a longer code, such as AD6.
            }
            var open = skipSpaces(caps, at + 2);
            if (open >= caps.length() || caps.charAt(open) != '(') {
                continue; // D6 without a value list.
            }
            var close = caps.indexOf(')', open);
            if (close < 0) {
                return STANDBY_OFF;
            }
            var values = List.of(caps.substring(open + 1, close).trim().split("\\s+"));
            return values.contains("05") && !values.contains("04") ? POWER_OFF : STANDBY_OFF;
        }
        return STANDBY_OFF;
    }

    /**
     * Whether power mode {@code value} means on: true for {@link #ON}, false for the standby, suspend and off values
     * 02-05, null for anything else (some monitors answer 0 whatever their state).
     */
    public static @Nullable Boolean isOn(int value) {
        if (value == ON) {
            return true;
        }
        return value >= 2 && value <= POWER_OFF ? false : null;
    }

    /** The inside of the first {@code VCP(...)} group, nested groups included; empty when there is none. */
    private static String vcpSection(String caps) {
        var start = caps.indexOf("VCP(");
        if (start < 0) {
            return "";
        }
        var from = start + 4;
        var depth = 1;
        for (var i = from; i < caps.length(); i++) {
            var c = caps.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return caps.substring(from, i);
            }
        }
        return caps.substring(from); // Truncated reply: take what came.
    }

    private static int skipSpaces(String s, int from) {
        var i = from;
        while (i < s.length() && s.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    private static boolean isHex(char c) {
        return Character.digit(c, 16) >= 0;
    }
}
