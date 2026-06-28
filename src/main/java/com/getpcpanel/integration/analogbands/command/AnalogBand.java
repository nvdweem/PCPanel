package com.getpcpanel.integration.analogbands.command;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.commands.Commands;

/**
 * One position of a {@link CommandAnalogBands} stepped switch: a sub-range of the dial/slider travel
 * (expressed as percentages 0-100 of the full range), the commands fired when the control enters that
 * range, and an optional LED feedback colour shown while the control rests on this position.
 *
 * <p>Bands may leave gaps between them; a gap acts as a dead zone (entering it neither fires anything
 * nor changes the displayed colour), which keeps a noisy analog reading from oscillating between two
 * adjacent positions.
 */
public record AnalogBand(double start, double end, @Nullable String color, @Nullable Commands commands) {
    @JsonCreator
    public AnalogBand(
            @JsonProperty("start") double start,
            @JsonProperty("end") double end,
            @Nullable @JsonProperty("color") String color,
            @Nullable @JsonProperty("commands") Commands commands) {
        this.start = start;
        this.end = end;
        this.color = color;
        this.commands = commands;
    }

    /** Whether the given position (percentage 0-100 of the control's travel) falls within this band. */
    public boolean contains(double pct) {
        return pct >= start && pct <= end;
    }
}
