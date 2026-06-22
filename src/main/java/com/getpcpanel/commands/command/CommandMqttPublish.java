package com.getpcpanel.commands.command;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.mqtt.MqttService;
import com.getpcpanel.util.CdiHelper;
import com.getpcpanel.util.ValueInterpolator;

import lombok.Getter;
import lombok.ToString;

/**
 * Publishes an MQTT message to {@code topic}. The {@code payload} may contain <code>{{ value }}</code>,
 * which is replaced with the dial-mapped value (or the configured max on a button press). Uses the
 * existing MQTT connection from settings; a dial stream is debounced by {@link MqttService}.
 */
@Getter
@ToString(callSuper = true)
public class CommandMqttPublish extends CommandValueOutput {
    private final String topic;
    private final String payload;

    @JsonCreator
    public CommandMqttPublish(
            @JsonProperty("topic") String topic,
            @JsonProperty("payload") String payload,
            @JsonProperty("min") @Nullable Double min,
            @JsonProperty("max") @Nullable Double max,
            @JsonProperty("formula") @Nullable String formula,
            @JsonProperty("dialParams") @Nullable DialCommandParams dialParams) {
        super(min, max, formula, dialParams);
        this.topic = topic;
        this.payload = payload;
    }

    @Override
    protected void send(double value, boolean immediate) {
        if (StringUtils.isBlank(topic)) {
            return;
        }
        var body = ValueInterpolator.interpolate(StringUtils.defaultString(payload), value);
        CdiHelper.getBean(MqttService.class).send(topic, body, immediate);
    }

    @Override
    public String buildLabel() {
        return "MQTT: " + StringUtils.defaultString(topic);
    }
}
