package com.getpcpanel.sleepdetection;

/**
 * The panels went dark ({@code dark}: the PC was locked, suspended or its displays went off, and the lights were
 * switched off) or were lit again with their own lighting. {@code awake} says, while dark, whether the PC is awake
 * (only a lock and/or screens off: features allowed to keep going may show on the dark panels, see
 * {@link SleepDetector#showsDarkFrames()}) or asleep (nothing shows); it is true when lit. Fired on the sleep detector's
 * lighting queue: before the lights-off, again when the PC goes to sleep or wakes while the panels stay dark, and after
 * the relight.
 */
public record PanelsDarkEvent(boolean dark, boolean awake) {
}
