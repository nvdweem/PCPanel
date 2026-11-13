package com.getpcpanel.device.provider.pcpanel;

import static com.getpcpanel.device.provider.pcpanel.OutputInterpreter.COLOR_STATIC;
import static com.getpcpanel.device.provider.pcpanel.OutputInterpreter.CUSTOM_KNOB;
import static com.getpcpanel.device.provider.pcpanel.OutputInterpreter.CUSTOM_LOGO;
import static com.getpcpanel.device.provider.pcpanel.OutputInterpreter.CUSTOM_SLIDER;
import static com.getpcpanel.device.provider.pcpanel.OutputInterpreter.CUSTOM_SLIDER_LABEL;
import static com.getpcpanel.device.provider.pcpanel.OutputInterpreter.LOGO_BREATH;
import static com.getpcpanel.device.provider.pcpanel.OutputInterpreter.LOGO_RAINBOW;
import static com.getpcpanel.device.provider.pcpanel.OutputInterpreter.MODE_LIGHT_ANIMATION;
import static com.getpcpanel.device.provider.pcpanel.OutputInterpreter.PREFIX_MINI;
import static com.getpcpanel.device.provider.pcpanel.OutputInterpreter.PREFIX_PRO;
import static com.getpcpanel.device.provider.pcpanel.OutputInterpreter.SLOT_LENGTH;
import static com.getpcpanel.device.provider.pcpanel.OutputInterpreter.STATIC_COLOR_MINI;
import static com.getpcpanel.device.provider.pcpanel.OutputInterpreter.STATIC_COLOR_PRO;

import java.util.HexFormat;
import java.util.StringJoiner;
import java.util.zip.CRC32;

/**
 * Reads a lighting state (the reports {@link OutputInterpreter} hands to a device in one go) back into whether it
 * tells the panel to show any light at all. Colours are already scaled by brightness when they are written
 * ({@link ByteWriter}), so a zero colour is a dark light whatever made it zero: a black colour, a brightness of
 * zero or an override. Only the Mini/Pro reports are read; anything else is {@link Darkness#UNKNOWN}.
 *
 * <p>A Pro whole-panel colour report starts with the same three bytes as a Pro vertical-rainbow report. It is read
 * as a colour: a rainbow report carries a {@code 0xFF} byte where the colour would be, so it always reads lit and is
 * never taken for dark.
 */
final class LightingFrames {
    /** Index of the colour in a whole-panel colour report, and of the colour in a static logo report. */
    private static final int COLOR_OFFSET = 3;
    private static final int RGB_LENGTH = 3;
    /** Index of the brightness byte in a whole-panel rainbow, wave or breath report and in a logo breath report. */
    private static final int ANIMATION_BRIGHTNESS = 5;
    /** Index of the brightness byte in a logo rainbow report (it has no hue byte). */
    private static final int LOGO_RAINBOW_BRIGHTNESS = 4;
    /** Per-control reports: the prefix, the kind, then one slot per light. */
    private static final int FIRST_SLOT = 2;
    private static final int LOGO_MODE = 2;
    private static final byte OFF = 0;

    enum Darkness {
        /** At least one light is told to show something. */
        LIT,
        /** Every light is told to show nothing. */
        DARK,
        /** Not a report this class can read. */
        UNKNOWN
    }

    private LightingFrames() {
    }

    static Darkness classify(byte[]... reports) {
        var unknown = false;
        for (var report : reports) {
            switch (classify(report)) {
                case LIT -> {
                    return Darkness.LIT;
                }
                case UNKNOWN -> unknown = true;
                case DARK -> { /* keep looking */ }
            }
        }
        return unknown || reports.length == 0 ? Darkness.UNKNOWN : Darkness.DARK;
    }

    private static Darkness classify(byte[] report) {
        if (report.length <= FIRST_SLOT || (report[0] != PREFIX_PRO && report[0] != PREFIX_MINI)) {
            return Darkness.UNKNOWN;
        }
        var kind = report[1];
        if (kind == MODE_LIGHT_ANIMATION) {
            return wholePanel(report);
        }
        if (kind == CUSTOM_LOGO) {
            return logo(report);
        }
        if (kind == CUSTOM_KNOB || kind == CUSTOM_SLIDER || kind == CUSTOM_SLIDER_LABEL) {
            return slots(report);
        }
        return Darkness.UNKNOWN;
    }

    private static Darkness wholePanel(byte[] report) {
        var staticColor = report[0] == PREFIX_PRO ? STATIC_COLOR_PRO : STATIC_COLOR_MINI;
        if (report[2] == staticColor) {
            return anyNonZero(report, COLOR_OFFSET, RGB_LENGTH);
        }
        return anyNonZero(report, ANIMATION_BRIGHTNESS, 1);
    }

    private static Darkness logo(byte[] report) {
        var mode = report[LOGO_MODE];
        if (mode == OFF) {
            return Darkness.DARK;
        }
        if (mode == COLOR_STATIC) {
            return anyNonZero(report, COLOR_OFFSET, RGB_LENGTH);
        }
        if (mode == LOGO_RAINBOW) {
            return anyNonZero(report, LOGO_RAINBOW_BRIGHTNESS, 1);
        }
        if (mode == LOGO_BREATH) {
            return anyNonZero(report, ANIMATION_BRIGHTNESS, 1);
        }
        return Darkness.UNKNOWN;
    }

    private static Darkness slots(byte[] report) {
        for (var slot = FIRST_SLOT; slot + SLOT_LENGTH <= report.length; slot += SLOT_LENGTH) {
            if (report[slot] != OFF && anyNonZero(report, slot + 1, SLOT_LENGTH - 1) == Darkness.LIT) {
                return Darkness.LIT;
            }
        }
        return Darkness.DARK;
    }

    private static Darkness anyNonZero(byte[] report, int from, int length) {
        for (var i = from; i < from + length && i < report.length; i++) {
            if (report[i] != 0) {
                return Darkness.LIT;
            }
        }
        return Darkness.DARK;
    }

    /** A short stable fingerprint of the reports, so a trace can pair a requested state with its write. */
    static String fingerprint(byte[]... reports) {
        var crc = new CRC32();
        for (var report : reports) {
            crc.update(report);
        }
        return "%08x".formatted(crc.getValue());
    }

    /** The reports as hex, each without its trailing zero padding, separated by {@code |}. */
    static String hex(byte[]... reports) {
        var joiner = new StringJoiner("|");
        for (var report : reports) {
            var end = report.length;
            while (end > 0 && report[end - 1] == 0) {
                end--;
            }
            joiner.add(HexFormat.of().formatHex(report, 0, end));
        }
        return joiner.toString();
    }
}
