package com.getpcpanel.homeassistant.command;

import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.commands.command.DialAction;

import lombok.Getter;
import lombok.ToString;
import net.objecthunter.exp4j.ExpressionBuilder;

/**
 * Dial action that performs a Home Assistant action with a numeric value derived from the analog
 * position. The user pastes the action YAML (from HA's Developer Tools → Actions) and writes
 * <code>{{ value }}</code> wherever the mapped number should go. The dial's normalised position
 * {@code x} (0..1, after trim/invert/range) is translated to that number either linearly between
 * {@code min} and {@code max}, or via the {@code formula} (with {@code x} the 0..1 position), then
 * substituted into the YAML before it is sent.
 */
@Getter
@ToString(callSuper = true)
public class CommandHomeAssistantValue extends CommandHomeAssistant implements DialAction {
    /** Matches {@code {{ value }}} with any inner whitespace; substituted before the YAML is parsed. */
    private static final Pattern VALUE_TOKEN = Pattern.compile("\\{\\{\\s*value\\s*}}");

    private final String action;
    @Nullable private final Double min;
    @Nullable private final Double max;
    @Nullable private final String formula;
    private final DialCommandParams dialParams;

    @JsonCreator
    public CommandHomeAssistantValue(
            @JsonProperty("server") @Nullable String server,
            @JsonProperty("action") String action,
            @JsonProperty("min") @Nullable Double min,
            @JsonProperty("max") @Nullable Double max,
            @JsonProperty("formula") @Nullable String formula,
            @JsonProperty("dialParams") DialCommandParams dialParams) {
        super(server);
        this.action = action;
        this.min = min;
        this.max = max;
        this.formula = formula;
        this.dialParams = dialParams;
    }

    @Override
    public void execute(DialActionParameters context) {
        if (StringUtils.isBlank(action)) {
            return;
        }
        var x = context.dial().getValue(this, 0f, 1f); // normalised 0..1, honouring trim/invert/range
        var value = translate(x);
        var yaml = VALUE_TOKEN.matcher(action).replaceAll(format(value));
        // A moving dial fires a stream of events; the throttle (configurable on the HA settings page)
        // sends the first instantly, gates the middle, and guarantees the final value. Keyed by this
        // command instance so each control throttles independently.
        service().callActionThrottled(this, server, yaml);
    }

    private double translate(double x) {
        if (StringUtils.isNotBlank(formula)) {
            try {
                return new ExpressionBuilder(formula).variable("x").build().setVariable("x", x).evaluate();
            } catch (RuntimeException e) {
                // Fall through to the linear mapping rather than dropping the action on a typo'd formula.
                return linear(x);
            }
        }
        return linear(x);
    }

    private double linear(double x) {
        var lo = min == null ? 0d : min;
        var hi = max == null ? 100d : max;
        return lo + x * (hi - lo);
    }

    /** Whole numbers as integers (Home Assistant fields like brightness reject floats), else decimal. */
    private static String format(double value) {
        if (Double.isFinite(value) && value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    @Override
    public String buildLabel() {
        return "HA: " + StringUtils.substringBefore(StringUtils.defaultString(action).strip(), "\n");
    }
}
