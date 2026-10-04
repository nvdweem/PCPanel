package com.getpcpanel.profile.dto;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

/**
 * Lights a control while an app wants attention. Applies to every device and every profile.
 *
 * @param trigger          what sets it off
 * @param app              the app (exe name); blank for {@link AlertTrigger#MIC_IN_USE} and
 *                         {@link AlertTrigger#WINDOW_TITLE} means any app
 * @param target           the light: {@code knob:<n>}, {@code slider:<n>} (0-based) or {@code logo}
 * @param color            the alert colour
 * @param blink            blink instead of a steady light; read when {@code effect} is not set (older saves)
 * @param stopAfterSeconds a taskbar-flash or notification alert clears after this long even if the app is not
 *                         opened; null or 0 keeps it until the app is focused
 * @param disabled         switched off without deleting it
 * @param exceptApps       for {@link AlertTrigger#MIC_IN_USE} with a blank app: apps that do not count, such as a
 *                         mixer that always holds the microphone (exe names or Store app ids)
 * @param effect           how the light shows; null falls back to {@code blink}
 * @param periodMs         length of one blink or pulse cycle in milliseconds; see {@link #periodOrDefault()}
 * @param pattern          text the trigger has to match, for triggers that look at text: for
 *                         {@link AlertTrigger#WINDOW_TITLE}, see {@link #titleMatches(String, String)}
 * @param source           where the trigger comes from when that is not the app itself: for
 *                         {@link AlertTrigger#NOTIFICATION}, the notification sender (a Windows handler id such as
 *                         {@code Microsoft.Teams}, or a Linux app name), compared ignoring case; blank uses the app
 * @param blinkColor       for {@link AlertEffect#BLINK}, the colour of the second half of each period; null shows black
 * @param brightness       how bright the light shows, 1–100, whatever the panel's brightness is; null follows the panel
 */
public record NotificationAlert(
        AlertTrigger trigger,
        @Nullable String app,
        String target,
        String color,
        boolean blink,
        @Nullable Integer stopAfterSeconds,
        boolean disabled,
        @Nullable List<String> exceptApps,
        @Nullable AlertEffect effect,
        @Nullable Integer periodMs,
        @Nullable String pattern,
        @Nullable String source,
        @Nullable String blinkColor,
        @Nullable Integer brightness) {
    public static final int DEFAULT_PERIOD_MS = 1_200;
    /** The blink rhythm before effects existed: half a second on, half a second off. */
    public static final int LEGACY_BLINK_PERIOD_MS = 1_000;
    public static final int MIN_PERIOD_MS = 200;
    public static final int MAX_PERIOD_MS = 10_000;
    public static final int MIN_BRIGHTNESS = 1;
    public static final int MAX_BRIGHTNESS = 100;
    /** The colour of a blink's second half when it has none of its own. */
    public static final String BLINK_OFF_COLOR = "#000000";
    /** The wildcard in a window-title pattern: any text, including none. */
    public static final char TITLE_WILDCARD = '*';
    /** An unread count in a window title, such as the {@code (3)} in {@code Inbox (3) - Outlook}. */
    private static final Pattern UNREAD_COUNT = Pattern.compile("\\(\\d+\\)");

    public NotificationAlert(AlertTrigger trigger, @Nullable String app, String target, String color, boolean blink, @Nullable Integer stopAfterSeconds) {
        this(trigger, app, target, color, blink, stopAfterSeconds, false, null);
    }

    public NotificationAlert(AlertTrigger trigger, @Nullable String app, String target, String color, boolean blink, @Nullable Integer stopAfterSeconds,
            boolean disabled, @Nullable List<String> exceptApps) {
        this(trigger, app, target, color, blink, stopAfterSeconds, disabled, exceptApps, null, null, null, null);
    }

    public NotificationAlert(AlertTrigger trigger, @Nullable String app, String target, String color, boolean blink, @Nullable Integer stopAfterSeconds,
            boolean disabled, @Nullable List<String> exceptApps, @Nullable AlertEffect effect, @Nullable Integer periodMs, @Nullable String pattern,
            @Nullable String source) {
        this(trigger, app, target, color, blink, stopAfterSeconds, disabled, exceptApps, effect, periodMs, pattern, source, null, null);
    }

    /** The colour of a blink's second half: {@code blinkColor}, or black. */
    public String blinkColorOrOff() {
        return StringUtils.isBlank(blinkColor) ? BLINK_OFF_COLOR : blinkColor;
    }

    /** The brightness, clamped to {@value #MIN_BRIGHTNESS}–{@value #MAX_BRIGHTNESS}; null follows the panel. */
    @Nullable
    public Integer brightnessOrNull() {
        return brightness == null ? null : Math.clamp(brightness, MIN_BRIGHTNESS, MAX_BRIGHTNESS);
    }

    public AlertEffect effectOrDefault() {
        if (effect != null) {
            return effect;
        }
        return blink ? AlertEffect.BLINK : AlertEffect.STEADY;
    }

    /** The period, clamped; unset is {@value #DEFAULT_PERIOD_MS}, or {@value #LEGACY_BLINK_PERIOD_MS} for a blink from an older save. */
    public int periodOrDefault() {
        if (periodMs == null) {
            return effect == null && blink ? LEGACY_BLINK_PERIOD_MS : DEFAULT_PERIOD_MS;
        }
        return Math.clamp(periodMs, MIN_PERIOD_MS, MAX_PERIOD_MS);
    }

    /**
     * Whether a window title matches {@code pattern}, ignoring case: a blank one wants an unread count like
     * {@code (3)}; one with {@code *} is matched as a whole against some part of the title, each {@code *} standing for
     * any text (none included), so {@code (*)} matches {@code Inbox (3) - Mail}; any other is looked for anywhere in the
     * title. Every other character stands for itself.
     */
    public static boolean titleMatches(@Nullable String pattern, String title) {
        if (StringUtils.isBlank(pattern)) {
            return UNREAD_COUNT.matcher(title).find();
        }
        var text = pattern.strip().toLowerCase(Locale.ROOT);
        var lower = title.toLowerCase(Locale.ROOT);
        if (text.indexOf(TITLE_WILDCARD) < 0) {
            return lower.contains(text);
        }
        var from = 0;
        for (var part : StringUtils.split(text, TITLE_WILDCARD)) {
            var at = lower.indexOf(part, from);
            if (at < 0) {
                return false;
            }
            from = at + part.length();
        }
        return true;
    }

    public List<String> exceptAppsOrEmpty() {
        return exceptApps == null ? List.of() : exceptApps;
    }

    public enum AlertTrigger {
        /** The app flashes its taskbar button (how chat apps signal a new message). Clears when the app gets focus. */
        TASKBAR_FLASH,
        /** The app (or any app, when blank) is using a microphone. Lit for as long as it is. */
        MIC_IN_USE,
        /**
         * The app shows a new desktop notification. Clears when the app gets focus, when its notifications are gone,
         * or after the stop-after time.
         */
        NOTIFICATION,
        /**
         * One of the app's windows (any app's, when blank) has a title matching {@code pattern} (see {@link #titleMatches(String, String)}).
         * Lit for as long as it does.
         */
        WINDOW_TITLE
    }

    public enum AlertEffect {
        /** On at full colour. */
        STEADY,
        /** On for the first half of each period, off for the second. */
        BLINK,
        /** Fades between dim and full colour once per period. */
        PULSE
    }
}
