package com.getpcpanel.sleepdetection;

/**
 * Platform-neutral system power/session events. Each OS-specific {@code *SystemEventService} fires
 * these on the CDI event bus; {@link SleepDetector} reacts to them. Not every platform can produce
 * every value (e.g. the callback-free macOS detection cannot see {@link #goingToSuspend} in
 * advance — it only learns of a suspend after the fact via {@link #resumedFromSuspend}).
 */
public enum SystemEventType {
    goingToSuspend,
    resumedFromSuspend,
    locked,
    unlocked,
    /** The displays were powered off (monitor idle-timeout, manual sleep, DPMS) — distinct from suspend. */
    displayOff,
    /** The displays were powered back on. */
    displayOn,
    logon,
    logoff
}
