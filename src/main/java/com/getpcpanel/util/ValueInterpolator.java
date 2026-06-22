package com.getpcpanel.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import net.objecthunter.exp4j.ExpressionBuilder;

/**
 * Maps a normalised dial position (0..1) to a target number and substitutes it for the
 * <code>{{ value }}</code> token in user-supplied templates (HTTP url/headers/body, MQTT payload, …).
 *
 * <p>The number is either a linear interpolation between {@code min} and {@code max}, or the result of
 * an {@code exp4j} {@code formula} with the variable {@code x} bound to the 0..1 position. This mirrors
 * the Home Assistant value command so every value-driven output behaves identically.
 */
public final class ValueInterpolator {
    /** Matches {@code {{ value }}} with any inner whitespace. */
    private static final Pattern VALUE_TOKEN = Pattern.compile("\\{\\{\\s*value\\s*}}");

    private ValueInterpolator() {
    }

    /** Translate the 0..1 position {@code x} via {@code formula} (variable {@code x}) or linearly between min/max. */
    public static double translate(double x, @Nullable Double min, @Nullable Double max, @Nullable String formula) {
        if (StringUtils.isNotBlank(formula)) {
            try {
                return new ExpressionBuilder(formula).variable("x").build().setVariable("x", x).evaluate();
            } catch (RuntimeException e) {
                // Fall through to the linear mapping rather than dropping the action on a typo'd formula.
                return linear(x, min, max);
            }
        }
        return linear(x, min, max);
    }

    private static double linear(double x, @Nullable Double min, @Nullable Double max) {
        var lo = min == null ? 0d : min;
        var hi = max == null ? 100d : max;
        return lo + x * (hi - lo);
    }

    /** Whole numbers as integers (so {@code {{ value }}} renders "50" not "50.0"), else decimal. */
    public static String format(double value) {
        if (Double.isFinite(value) && value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    /** Substitute every {@code {{ value }}} token in {@code template} with {@code value}; null-safe. */
    @Nullable
    public static String interpolate(@Nullable String template, double value) {
        if (template == null) {
            return null;
        }
        return VALUE_TOKEN.matcher(template).replaceAll(Matcher.quoteReplacement(format(value)));
    }
}
