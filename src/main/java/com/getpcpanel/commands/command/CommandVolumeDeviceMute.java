package com.getpcpanel.commands.command;

import com.getpcpanel.cpp.MuteType;
import com.getpcpanel.cpp.SndCtrl;

import lombok.Getter;

@Getter
public class CommandVolumeDeviceMute extends CommandVolume {
    private final String deviceId;
    private final MuteType muteType;

    public CommandVolumeDeviceMute(String device, int knob, String deviceId, MuteType muteType) {
        super(device, knob);
        this.deviceId = deviceId;
        this.muteType = muteType;
    }

    @Override
    public void execute() {
        SndCtrl.muteDevice(deviceId, muteType);
    }
}
