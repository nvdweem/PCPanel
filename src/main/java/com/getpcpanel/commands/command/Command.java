package com.getpcpanel.commands.command;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import lombok.ToString;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ToString
@SuppressWarnings("InstanceofThis")
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "_type")
public abstract class Command {
    public Runnable toRunnable(String deviceId, Integer vol) {
        if (this instanceof ButtonAction ba) {
            return ba.toRunnable();
        }
        if (this instanceof DeviceAction da) {
            return da.toRunnable(deviceId);
        }
        if (vol != null && this instanceof DialAction da) {
            return da.toRunnable(vol);
        }
        log.error("Unable to convert {} to Runnable ({}, {})", this, deviceId, vol);
        return () -> {
        };
    }
}
