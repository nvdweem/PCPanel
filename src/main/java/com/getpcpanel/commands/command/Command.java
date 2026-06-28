package com.getpcpanel.commands.command;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.getpcpanel.commands.DialValue;

import lombok.ToString;
import lombok.extern.log4j.Log4j2;

/**
 * Base type for every assignable action.
 *
 * <p><b>Polymorphism is fully decentralized.</b> The discriminator uses {@link JsonTypeInfo.Id#NAME},
 * and each concrete command declares its own stable id with a {@code @JsonTypeName} <em>in its own
 * package</em> — there is deliberately no central {@code @JsonSubTypes} list here, so adding a command
 * (or a whole new feature module) never touches this class. The id is a stable, location-independent
 * string equal to the command's historical fully-qualified name, so saved {@code profiles.json}, the
 * generated TS {@code _type} union, and the frontend catalog are unaffected by where the class lives.
 *
 * <p>Each feature module contributes its command classes through the {@link com.getpcpanel.commands.CommandModule}
 * CDI SPI; {@link com.getpcpanel.commands.CommandSubtypeRegistrar} collects them via {@code @All} and
 * registers them with Jackson for deserialization. {@code typescript-generator} discovers the subtypes
 * from the {@code **.command.**} class scan and reads each {@code @JsonTypeName}.
 */
@Log4j2
@ToString
@SuppressWarnings("InstanceofThis")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "_type")
public abstract class Command {
    public Runnable toRunnable(boolean initial, String deviceId, @Nullable DialValue vol) {
        // A command that is both a dial and a button action (e.g. the generic HTTP/MQTT/OSC outputs)
        // runs as a dial when a dial value is present, so {{value}} maps from the knob position; on a
        // button (vol == null) it falls through to the button path. Single-role commands are unaffected.
        if (vol != null && this instanceof DialAction da) {
            return da.toRunnable(new DialAction.DialActionParameters(deviceId, initial, vol));
        }
        if (this instanceof ButtonAction ba) {
            return ba.toRunnable();
        }
        if (this instanceof DeviceAction da) {
            return da.toRunnable(new DeviceAction.DeviceActionParameters(deviceId));
        }
        log.error("Unable to convert {} to Runnable ({}, {})", this, deviceId, vol);
        return () -> {
        };
    }

    public abstract String buildLabel();
}
