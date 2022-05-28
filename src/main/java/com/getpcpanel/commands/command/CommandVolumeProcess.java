package com.getpcpanel.commands.command;

import com.getpcpanel.cpp.SndCtrl;

import lombok.Getter;

@Getter
public class CommandVolumeProcess extends CommandVolume {
    private final String processName;
    private final String device;
    private final int volume;

    public CommandVolumeProcess(String serial, int knob, String processName, String device, int volume) {
        super(serial, knob);
        this.processName = processName;
        this.device = device;
        this.volume = volume;
    }

    @Override
    public void execute() {
        SndCtrl.setProcessVolume(processName, device, volume / 100f);
    }
}
