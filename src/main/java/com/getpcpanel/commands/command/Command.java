package com.getpcpanel.commands.command;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.getpcpanel.hid.DialValue;

import lombok.ToString;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ToString
@SuppressWarnings("InstanceofThis")
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "_type")
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
