package com.getpcpanel.sleepdetection;

/**
 * The panels went dark ({@code dark}: the PC was locked, suspended or its displays went off, and the lights were
 * switched off) or were lit again with their own lighting. Fired on the sleep detector's lighting queue: before the
 * lights-off, and after the relight.
 */
public record PanelsDarkEvent(boolean dark) {
}
