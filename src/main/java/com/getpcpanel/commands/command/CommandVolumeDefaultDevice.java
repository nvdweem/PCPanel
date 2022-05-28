package com.getpcpanel.commands.command;

import com.getpcpanel.cpp.SndCtrl;

import lombok.Getter;

@Getter
public class CommandVolumeDefaultDevice extends CommandVolume {
    private final String deviceId;

    public CommandVolumeDefaultDevice(String device, int knob, String deviceId) {
        super(device, knob);
        this.deviceId = deviceId;
    }

    @Override
    public void execute() {
        SndCtrl.setDefaultDevice(deviceId);
    }
}
