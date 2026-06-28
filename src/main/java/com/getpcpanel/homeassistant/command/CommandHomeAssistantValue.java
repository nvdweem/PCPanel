package com.getpcpanel.homeassistant.command;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.meta.CommandCategory;
import com.getpcpanel.commands.meta.CommandKind;
import com.getpcpanel.commands.meta.CommandMeta;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.commands.command.DialAction;
import com.getpcpanel.util.ValueInterpolator;

import lombok.Getter;
import lombok.ToString;

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
@JsonTypeName("homeassistant.value")
@CommandMeta(label = "Home Assistant — set value", category = CommandCategory.integration, kinds = {CommandKind.dial}, integration = "homeassistant", icon = "sliders", legacyIds = {"com.getpcpanel.homeassistant.command.CommandHomeAssistantValue"})
public class CommandHomeAssistantValue extends CommandHomeAssistant implements DialAction {
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
        var yaml = ValueInterpolator.interpolate(action, ValueInterpolator.translate(x, min, max, formula));
        // A moving dial fires a stream of events; the throttle (configurable on the HA settings page)
        // sends the first instantly, gates the middle, and guarantees the final value. Keyed by a stable
        // value (server + action) rather than `this`: a fresh command instance is deserialised on every
        // profile edit/reload, so an identity key would strand a dead entry in the shared Debouncer map.
        // (A plain String, not a record — a nested record here would be scraped into the generated TS.)
        service().callActionThrottled("ha " + server + ' ' + action, server, yaml);
    }

    @Override
    public String buildLabel() {
        return "HA: " + StringUtils.substringBefore(StringUtils.defaultString(action).strip(), "\n");
    }
}
