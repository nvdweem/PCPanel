package com.getpcpanel.profile.dto;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A profile's music visualizer: panel lights that move with what the PC plays. Saved inside the profile's
 * {@link LightingConfig}; absent means off.
 */
@Data
@NoArgsConstructor
public class VisualizerConfig {
    public static final String DEFAULT_LOW = "#2040FF";
    public static final String DEFAULT_HIGH = "#FF2D95";

    private VisualizerWhen when = VisualizerWhen.OFF;
    /**
     * What it listens to, in order: the first that has sound. Null in a save from before this list, which
     * {@link #getSources()} reads from {@link #apps}.
     */
    @Getter(AccessLevel.NONE)
    private VisualizerSource[] sources;
    /** Saves from before {@link #sources}: only these apps counted (empty is any app). Read, never written. */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @JsonIgnore
    @Nullable private String[] apps;
    /** The lights it drives, as {@code knob:N}, {@code slider:N}, {@code label:N} or {@code logo}; empty is all. */
    private String[] lights = {};
    private VisualizerStyle style = VisualizerStyle.RAINBOW;
    /** {@link VisualizerStyle#TWO_COLORS}: the quiet colour. */
    @Nullable private String lowColor = DEFAULT_LOW;
    /** {@link VisualizerStyle#TWO_COLORS}: the loud colour; {@link VisualizerStyle#PULSE}: the colour. */
    @Nullable private String highColor = DEFAULT_HIGH;

    public enum VisualizerWhen {
        OFF,
        /** While one of its {@link #sources} has sound. */
        PLAYING,
        /** Whenever the profile is active; quiet is dark. */
        ALWAYS
    }

    public enum VisualizerStyle {
        /** Band hues from red (bass) to violet (treble). */
        RAINBOW,
        /** From {@link #lowColor} when quiet to {@link #highColor} when loud. */
        TWO_COLORS,
        /** Every light in {@link #highColor}, following the whole mix. */
        PULSE
    }

    /** A new visualizer: on while the default output plays. */
    public static VisualizerConfig create() {
        var c = new VisualizerConfig();
        c.sources = new VisualizerSource[] { VisualizerSource.output(null) };
        return c;
    }

    @JsonAnySetter
    void readLegacy(String name, Object value) {
        if ("apps".equals(name) && value instanceof List<?> list) {
            apps = list.stream().filter(String.class::isInstance).map(String.class::cast).toArray(String[]::new);
        }
    }

    /** What it listens to, in order. A save from before this list reads its apps: one source each, or else any app. */
    @Nonnull
    public VisualizerSource[] getSources() {
        if (sources != null) {
            return sources;
        }
        if (apps != null && apps.length > 0) {
            return Arrays.stream(apps).map(VisualizerSource::app).toArray(VisualizerSource[]::new);
        }
        return new VisualizerSource[] { VisualizerSource.anyApp() };
    }

    public VisualizerConfig copy() {
        var c = new VisualizerConfig();
        c.when = when;
        c.sources = getSources().clone();
        c.lights = lights == null ? new String[0] : lights.clone();
        c.style = style;
        c.lowColor = lowColor;
        c.highColor = highColor;
        return c;
    }

    /** Whether it is switched on at all. */
    public boolean enabled() {
        return when != null && when != VisualizerWhen.OFF;
    }
}
