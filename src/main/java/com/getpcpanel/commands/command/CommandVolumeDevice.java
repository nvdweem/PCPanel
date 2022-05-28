package com.getpcpanel.commands.command;

import com.getpcpanel.cpp.SndCtrl;

import lombok.Getter;

@Getter
public class CommandVolumeDevice extends CommandVolume {
    private final String deviceId;
    private final int volume;

    public CommandVolumeDevice(String device, int knob, String deviceId, int volume) {
        super(device, knob);
        this.deviceId = deviceId;
        this.volume = volume;
    }

    @Override
    public void execute() {
        SndCtrl.setDeviceVolume(deviceId, volume / 100f);
    }
}
