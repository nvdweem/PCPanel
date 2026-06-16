package com.getpcpanel.sleepdetection;

/**
 * Platform-neutral system power/session events. Each OS-specific {@code *SystemEventService} fires
 * these on the CDI event bus; {@link SleepDetector} reacts to them. Not every platform can produce
 * every value (e.g. the callback-free Windows/macOS detection cannot see {@link #goingToSuspend} in
 * advance — it only learns of a suspend after the fact via {@link #resumedFromSuspend}).
 */
public enum SystemEventType {
    goingToSuspend,
    resumedFromSuspend,
    locked,
    unlocked,
    logon,
    logoff
}
