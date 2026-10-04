package com.getpcpanel.commands;

/**
 * A control moved while one of its actions waits for soft takeover ({@link SoftTakeover}): {@code control} is the
 * reading with all of the control's actions, {@code current} the waiting target's level (0..1) the control has to
 * reach. Shown by the overlay so the user can see where to move to.
 */
public record TakeoverPendingEvent(PCPanelControlEvent control, float current) {
}
