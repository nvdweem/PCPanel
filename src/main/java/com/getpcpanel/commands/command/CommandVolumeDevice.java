package com.getpcpanel.commands.command;

import com.getpcpanel.cpp.SndCtrl;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class CommandVolumeDevice extends CommandVolume implements DialAction {
    private final String deviceId;

    public CommandVolumeDevice(String deviceId) {
        this.deviceId = deviceId;
    }

    @Override
    public void execute(int volume) {
        SndCtrl.setDeviceVolume(deviceId, volume / 100f);
    }
}
