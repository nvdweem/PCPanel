package com.getpcpanel.commands.command;

import com.getpcpanel.cpp.SndCtrl;

import lombok.Getter;

@Getter
public class CommandVolumeProcess extends CommandVolume {
    private final String processName;
    private final int volume;

    public CommandVolumeProcess(String device, int knob, String processName, int volume) {
        super(device, knob);
        this.processName = processName;
        this.volume = volume;
    }

    @Override
    public void execute() {
        SndCtrl.setProcessVolume(processName, volume / 100f);
    }
}
