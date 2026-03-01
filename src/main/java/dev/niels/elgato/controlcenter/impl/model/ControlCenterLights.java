package dev.niels.elgato.controlcenter.impl.model;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import java.util.Objects;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.getpcpanel.util.Util;

import lombok.With;

@JsonInclude(NON_NULL)
public record ControlCenterLights(
        @Nullable Integer brightness,
        @Nullable Integer brightnessMax,
        @Nullable Integer brightnessMin,
        @Nullable @With Boolean on,
        @Nullable Integer temperature,
        @Nullable Integer temperatureMax,
        @Nullable Integer temperatureMin
) {
    public static final ControlCenterLights EMPTY = new ControlCenterLights(null, null, null, null, null, null, null);
    public static final ControlCenterLights OFF = new ControlCenterLights(null, null, null, false, null, null, null);
    public static final ControlCenterLights ON = new ControlCenterLights(null, null, null, true, null, null, null);

    public static ControlCenterLights state(boolean state) {
        return state ? ON : OFF;
    }

    public static ControlCenterLights brightness(int brightness) {
        return new ControlCenterLights(brightness, null, null, null, null, null, null);
    }

    public static ControlCenterLights temperature(int temperature) {
        return new ControlCenterLights(null, null, null, null, temperature, null, null);
    }

    public ControlCenterLights withBrightness(double value, boolean minIsOff, boolean changeIsOn) {
        return withValue(value, ControlCenterLights::brightness, minIsOff, changeIsOn, Objects.requireNonNullElse(brightnessMin, 0), Objects.requireNonNullElse(brightnessMax, 100));
    }

    public ControlCenterLights withTemperature(double value, boolean minIsOff, boolean changeIsOn) {
        return withValue(value, ControlCenterLights::temperature, minIsOff, changeIsOn, Objects.requireNonNullElse(temperatureMin, 7000), Objects.requireNonNullElse(temperatureMax, 2900));
    }

    private ControlCenterLights withValue(double value, Function<Integer, ControlCenterLights> setter, boolean minIsOff, boolean changeIsOn, int min, int max) {
        var newValue = (int) Util.map(value, 0, 1, min, max);

        var result = setter.apply(newValue);
        if (minIsOff && Objects.equals(newValue, min)) {
            return result.withOn(false);
        }
        if (changeIsOn) {
            return result.withOn(true);
        }
        return result;
    }
}
