package com.getpcpanel.commands;

/**
 * A control moved while one of its actions waits for soft takeover ({@link SoftTakeover}): {@code control} is the
 * reading with all of the control's actions, {@code position} the raw control position (0..255) at which it reaches
 * the waiting target's level and takes over. Shown by the overlay so the user can see where to move to.
 */
public record TakeoverPendingEvent(PCPanelControlEvent control, int position) {
}
