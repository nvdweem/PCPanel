package com.getpcpanel.rest.model.dto;

import javax.annotation.Nullable;

/**
 * What this desktop can't detect, for the settings page to say next to the options that depend on it. Each is a sentence
 * for the user, or {@code null} when it works (or is not known to fail yet).
 *
 * @param screensOff    the screens turning off, for "Lights off when locked or asleep"
 * @param lockAndSleep  the PC being locked or going to sleep, for the same option
 * @param notifications apps' desktop notifications, for the notification lights
 */
public record PlatformLimitsDto(@Nullable String screensOff, @Nullable String lockAndSleep, @Nullable String notifications) {
}
