package com.getpcpanel.alerts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.profile.dto.NotificationAlert;
import com.getpcpanel.profile.dto.NotificationAlert.AlertEffect;
import com.getpcpanel.profile.dto.NotificationAlert.AlertTrigger;
import com.getpcpanel.util.ExeNames;
import com.getpcpanel.util.Util;

/**
 * Which alerts are lit, free of timers and I/O so it can be tested on its own. Alerts are identified by their
 * position in the configured list, switched-off ones included. Each trigger keeps the positions it has lit in
 * {@link #active}; a trigger lights alerts through {@link #light} (an event) or {@link #lightExactly} (a state).
 */
final class AlertState {
    /** How often the lights are redrawn when nothing needs it sooner. */
    static final long TICK_MS = 125;
    /** How often the lights are redrawn while one pulses. */
    static final long PULSE_TICK_MS = 50;
    /** Triggers set off by an event, lit until their app gets focus or the alert's stop-after time has passed. */
    private static final Set<AlertTrigger> UNTIL_FOCUSED = EnumSet.of(AlertTrigger.TASKBAR_FLASH, AlertTrigger.NOTIFICATION);

    private List<NotificationAlert> alerts = List.of();
    /** Per trigger, the alerts it lit, with when each was first lit. */
    private final Map<AlertTrigger, Map<Integer, Long>> active = new EnumMap<>(AlertTrigger.class);
    /** Alerts shown on request, with when that ends. */
    private final Map<Integer, Long> previews = new HashMap<>();
    /** The apps with notifications at the last look, each with its newest; null before the first look. */
    @Nullable private Map<String, Long> notificationsSeen;

    /** What the lights show at one moment, read in one go so a list change in between cannot mix two lists. */
    record Frame(Set<Integer> indexes, Map<String, String> colors, boolean pulsing, long nextTickMs) {
    }

    /**
     * Takes a new list. An alert that is still the same alert (same position, trigger and app, or else the same
     * trigger, app and light elsewhere in the list) stays lit through a change of colour, effect or the like;
     * switched-off and removed alerts go dark.
     */
    synchronized void configure(List<NotificationAlert> next) {
        if (next.equals(alerts)) {
            return;
        }
        var moved = carryOver(alerts, next);
        for (var lit : active.values()) {
            var kept = remap(lit, moved, next);
            lit.clear();
            lit.putAll(kept);
        }
        var keptPreviews = remap(previews, moved, null);
        previews.clear();
        previews.putAll(keptPreviews);
        alerts = List.copyOf(next);
    }

    /** For each old position, where that alert is in {@code next}, or -1 when it is gone. */
    private static int[] carryOver(List<NotificationAlert> old, List<NotificationAlert> next) {
        var moved = new int[old.size()];
        Arrays.fill(moved, -1);
        var taken = new boolean[next.size()];
        for (var i = 0; i < Math.min(old.size(), next.size()); i++) {
            if (sameSource(old.get(i), next.get(i))) {
                moved[i] = i;
                taken[i] = true;
            }
        }
        for (var i = 0; i < old.size(); i++) {
            for (var j = 0; moved[i] < 0 && j < next.size(); j++) {
                if (!taken[j] && sameSource(old.get(i), next.get(j)) && Objects.equals(old.get(i).target(), next.get(j).target())) {
                    moved[i] = j;
                    taken[j] = true;
                }
            }
        }
        return moved;
    }

    private static boolean sameSource(NotificationAlert a, NotificationAlert b) {
        return a.trigger() == b.trigger() && StringUtils.equalsIgnoreCase(StringUtils.strip(a.app()), StringUtils.strip(b.app()))
                && StringUtils.equalsIgnoreCase(StringUtils.stripToNull(a.source()), StringUtils.stripToNull(b.source()));
    }

    /** {@code byIndex} at the new positions; with {@code next}, alerts now switched off are left out. */
    private static Map<Integer, Long> remap(Map<Integer, Long> byIndex, int[] moved, @Nullable List<NotificationAlert> next) {
        var result = new HashMap<Integer, Long>();
        byIndex.forEach((i, v) -> {
            var j = i < moved.length ? moved[i] : -1;
            if (j >= 0 && (next == null || !next.get(j).disabled())) {
                result.put(j, v);
            }
        });
        return result;
    }

    synchronized boolean hasTrigger(AlertTrigger trigger) {
        return alerts.stream().anyMatch(a -> !a.disabled() && a.trigger() == trigger);
    }

    synchronized void onFlash(String exe, long now) {
        light(AlertTrigger.TASKBAR_FLASH, a -> sameApp(a.app(), exe), now);
    }

    /** The user switched to {@code app}: its alerts that wait for that have done their job. */
    synchronized void onFocus(@Nullable String app) {
        if (StringUtils.isBlank(app)) {
            return;
        }
        for (var trigger : UNTIL_FOCUSED) {
            litBy(trigger).keySet().removeIf(i -> sameApp(alerts.get(i).app(), app));
        }
    }

    synchronized void onMicUsers(Set<String> users, long now) {
        lightExactly(AlertTrigger.MIC_IN_USE, a -> micInUse(a, users), now);
    }

    /**
     * The visible top-level window titles per app (lower-case exe name without {@code .exe}): a window-title alert is
     * lit while one of its app's titles matches its pattern; one with a blank app looks at every app's titles.
     */
    synchronized void onTitles(Map<String, List<String>> titles, long now) {
        lightExactly(AlertTrigger.WINDOW_TITLE, a -> titles.entrySet().stream()
                                                           .anyMatch(e -> (StringUtils.isBlank(a.app()) || sameApp(a.app(), e.getKey()))
                                                                   && e.getValue().stream().anyMatch(t -> NotificationAlert.titleMatches(a.pattern(), t))), now);
    }

    /** {@link #onNotifications(Map, long)} for apps whose notifications are told apart only by coming and going. */
    synchronized void onNotifications(Set<String> apps, long now) {
        onNotifications(apps.stream().collect(Collectors.toMap(Function.identity(), app -> 0L, Math::max)), now);
    }

    /**
     * The apps showing notifications now, each with a number that grows with every new one. An alert lights when its
     * app (or its source) appears or shows a newer notification than at the previous look; the first look only
     * records what is there. A lit alert goes dark once its app has no notifications left.
     */
    synchronized void onNotifications(Map<String, Long> newest, long now) {
        var current = newest.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().toLowerCase(Locale.ROOT), Map.Entry::getValue, Math::max));
        var previous = notificationsSeen;
        notificationsSeen = current;
        litBy(AlertTrigger.NOTIFICATION).keySet().removeIf(i -> current.keySet().stream().noneMatch(app -> notifies(alerts.get(i), app)));
        if (previous == null) {
            return;
        }
        var fresh = current.entrySet().stream()
                           .filter(e -> !previous.containsKey(e.getKey()) || e.getValue() > previous.get(e.getKey()))
                           .map(Map.Entry::getKey)
                           .toList();
        if (!fresh.isEmpty()) {
            light(AlertTrigger.NOTIFICATION, a -> fresh.stream().anyMatch(app -> notifies(a, app)), now);
        }
    }

    /** Notifications are not being looked at; the next look is a first look again. */
    synchronized void forgetNotifications() {
        notificationsSeen = null;
    }

    /**
     * Whether notifications from {@code app} (lower-case) are for {@code alert}: its source when set, ignoring case,
     * else when {@code app} contains the alert app's exe name ({@code Discord.exe} matches
     * {@code com.squirrel.discord.discord}).
     */
    private static boolean notifies(NotificationAlert alert, String app) {
        if (StringUtils.isNotBlank(alert.source())) {
            return StringUtils.equalsIgnoreCase(alert.source().strip(), app);
        }
        if (StringUtils.isBlank(alert.app())) {
            return false;
        }
        var name = ExeNames.stem(alert.app()).toLowerCase(Locale.ROOT);
        return !name.isEmpty() && app.contains(name);
    }

    /** Lights the switched-on alerts of {@code trigger} that {@code matches}, on top of those already lit. */
    private void light(AlertTrigger trigger, Predicate<NotificationAlert> matches, long now) {
        var lit = litBy(trigger);
        for (var i = 0; i < alerts.size(); i++) {
            var a = alerts.get(i);
            if (!a.disabled() && a.trigger() == trigger && matches.test(a)) {
                lit.putIfAbsent(i, now);
            }
        }
    }

    /** Lights exactly the switched-on alerts of {@code trigger} that {@code matches}; its others go dark. */
    private void lightExactly(AlertTrigger trigger, Predicate<NotificationAlert> matches, long now) {
        var lit = litBy(trigger);
        var keep = new HashSet<Integer>();
        for (var i = 0; i < alerts.size(); i++) {
            var a = alerts.get(i);
            if (!a.disabled() && a.trigger() == trigger && matches.test(a)) {
                keep.add(i);
            }
        }
        lit.keySet().retainAll(keep);
        keep.forEach(i -> lit.putIfAbsent(i, now));
    }

    private Map<Integer, Long> litBy(AlertTrigger trigger) {
        return active.computeIfAbsent(trigger, t -> new HashMap<>());
    }

    /** Shows alert {@code index} until {@code until}, whether or not its trigger fired and even when switched off. */
    synchronized void preview(int index, long until) {
        if (index >= 0 && index < alerts.size()) {
            previews.put(index, until);
        }
    }

    /**
     * The alerts showing at {@code now}, by position in the configured list, after dropping those whose time is
     * up: the first lit alert for a target wins, a previewed one ahead of the rest. A blinking alert counts in its
     * off half too.
     */
    synchronized Set<Integer> litIndexes(long now) {
        for (var trigger : UNTIL_FOCUSED) {
            litBy(trigger).entrySet().removeIf(e -> expired(alerts.get(e.getKey()), e.getValue(), now));
        }
        previews.values().removeIf(until -> now >= until);
        var candidates = new ArrayList<Integer>(previews.keySet().stream().sorted().toList());
        for (var i = 0; i < alerts.size(); i++) {
            var index = i;
            if (!previews.containsKey(i) && active.values().stream().anyMatch(lit -> lit.containsKey(index))) {
                candidates.add(i);
            }
        }
        var targets = new HashSet<String>();
        var result = new LinkedHashSet<Integer>();
        for (var i : candidates) {
            var target = alerts.get(i).target();
            if (StringUtils.isNotBlank(target) && targets.add(target)) {
                result.add(i);
            }
        }
        return result;
    }

    /** {@link #lit(Set, long)} for what is showing at {@code now}. */
    synchronized Map<String, String> lit(long now) {
        return lit(litIndexes(now), now);
    }

    /** What is showing at {@code now}: which alerts, their colours, and when to redraw. */
    synchronized Frame frame(long now) {
        var indexes = litIndexes(now);
        return new Frame(indexes, lit(indexes, now), pulsing(indexes), nextTickMs(indexes, now));
    }

    /** The colour each target of {@code indexes} shows at {@code now}, scaled by its effect; absent while fully off. */
    private Map<String, String> lit(Set<Integer> indexes, long now) {
        var result = new LinkedHashMap<String, String>();
        for (var i : indexes) {
            var a = alerts.get(i);
            var color = scale(a.color(), intensity(a.effectOrDefault(), now, a.periodOrDefault()));
            if (color != null) {
                result.put(a.target(), color);
            }
        }
        return result;
    }

    /** Whether one of {@code indexes} pulses, so its light changes on every redraw. */
    private boolean pulsing(Set<Integer> indexes) {
        return indexes.stream().anyMatch(i -> alerts.get(i).effectOrDefault() == AlertEffect.PULSE);
    }

    /** How long after {@code now} the lights of {@code indexes} need redrawing: at the next blink edge, often while pulsing. */
    private long nextTickMs(Set<Integer> indexes, long now) {
        var next = TICK_MS;
        for (var i : indexes) {
            var a = alerts.get(i);
            next = switch (a.effectOrDefault()) {
                case STEADY -> next;
                case PULSE -> Math.min(next, PULSE_TICK_MS);
                case BLINK -> Math.min(next, toNextBlinkEdge(now, a.periodOrDefault()));
            };
        }
        return next;
    }

    private static long toNextBlinkEdge(long now, int periodMs) {
        var phase = Math.floorMod(now, periodMs);
        var half = periodMs / 2.0;
        var edge = phase < half ? half : periodMs;
        return Math.max(1, (long) Math.ceil(edge - phase));
    }

    /** How bright an effect is at {@code now}, from 0 (off) to 1 (full colour). */
    static double intensity(AlertEffect effect, long now, int periodMs) {
        var phase = (double) Math.floorMod(now, periodMs) / periodMs;
        return switch (effect) {
            case STEADY -> 1;
            case BLINK -> phase < 0.5 ? 1 : 0;
            case PULSE -> 0.12 + 0.88 * (0.5 - 0.5 * Math.cos(2 * Math.PI * phase));
        };
    }

    /** {@code color} with each channel scaled by {@code intensity}; null when off, unchanged at full intensity. */
    @Nullable
    private static String scale(String color, double intensity) {
        if (intensity <= 0) {
            return null;
        }
        var rgb = intensity >= 1 ? null : Util.parseColorComponents(color);
        if (rgb == null) {
            return color;
        }
        return Util.formatHexString(scaleChannel(rgb[0], intensity), scaleChannel(rgb[1], intensity), scaleChannel(rgb[2], intensity));
    }

    private static int scaleChannel(int value, double intensity) {
        return (int) Math.round(value * intensity);
    }

    /** A blank app means any app not in the exceptions. Mic users can be Store app ids, so those match by name too. */
    private static boolean micInUse(NotificationAlert alert, Set<String> users) {
        if (StringUtils.isNotBlank(alert.app())) {
            return users.stream().anyMatch(u -> sameMicApp(alert.app(), u));
        }
        return users.stream().anyMatch(u -> alert.exceptAppsOrEmpty().stream().noneMatch(e -> sameMicApp(e, u)));
    }

    /**
     * {@link #sameApp}, plus a Store app id ({@code Elgato.WaveLink_g54w8ztgkx496}) matching the exe it runs
     * ({@code WaveLink.exe}) by the last part of its name.
     */
    static boolean sameMicApp(@Nullable String configured, @Nullable String user) {
        if (sameApp(configured, user)) {
            return true;
        }
        if (StringUtils.isBlank(configured) || StringUtils.isBlank(user)) {
            return false;
        }
        return StringUtils.equalsIgnoreCase(storeAppName(ExeNames.stem(configured)), storeAppName(ExeNames.stem(user)));
    }

    /** {@code Elgato.WaveLink_g54w8ztgkx496} → {@code WaveLink}; a plain name stays as it is. */
    private static String storeAppName(String name) {
        var family = name.contains("_") ? StringUtils.substringBeforeLast(name, "_") : name;
        return StringUtils.substringAfterLast("." + family, ".");
    }

    private static boolean expired(NotificationAlert alert, long since, long now) {
        var stop = alert.stopAfterSeconds();
        return stop != null && stop > 0 && now - since >= stop * 1000L;
    }

    /** Executable names compared without path, case or a trailing {@code .exe}. */
    static boolean sameApp(@Nullable String configured, @Nullable String actual) {
        return ExeNames.sameApp(configured, actual);
    }
}
