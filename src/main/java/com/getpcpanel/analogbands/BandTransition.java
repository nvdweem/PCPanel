package com.getpcpanel.analogbands;

/**
 * Result of feeding one analog reading to a {@link com.getpcpanel.commands.command.CommandAnalogBands}: the
 * now-selected position, whether
 * the selected position changed (so per-position LED feedback should refresh), and whether the entered
 * band's commands should fire.
 *
 * <p>Deliberately lives outside {@code com.getpcpanel.commands.command} so it is not emitted into the
 * generated TypeScript contract — it is a backend-internal return type, not part of the saved command shape.
 */
public record BandTransition(int band, boolean changed, boolean fire) {
}
