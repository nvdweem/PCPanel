package com.getpcpanel.sleepdetection;

/** A power/session change fired on the CDI event bus by the platform sleep-detection services. */
public record SystemEvent(SystemEventType type) {
}
