package com.getpcpanel.commands.command;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

@Getter
@Log4j2
@ToString(callSuper = true)
public class CommandVolumeDefaultDeviceToggle extends CommandVolume implements ButtonAction {
    private final List<String> devices;

    @JsonCreator
    public CommandVolumeDefaultDeviceToggle(@JsonProperty("devices") List<String> devices) {
        this.devices = devices;
    }

    @Override
    public void execute() {
        // TODO
        log.error("TODO");
        // SndCtrl.setDefaultDevice(deviceId);
    }
}
