package com.getpcpanel.commands.command;

import com.getpcpanel.cpp.SndCtrl;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class CommandVolumeDefaultDevice extends CommandVolume implements ButtonAction {
    private final String deviceId;

    public CommandVolumeDefaultDevice(String deviceId) {
        this.deviceId = deviceId;
    }

    @Override
    public void execute() {
        SndCtrl.setDefaultDevice(deviceId);
    }
}
