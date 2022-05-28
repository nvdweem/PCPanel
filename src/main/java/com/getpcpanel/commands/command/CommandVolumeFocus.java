package com.getpcpanel.commands.command;

import com.getpcpanel.cpp.SndCtrl;

import lombok.Getter;

@Getter
public class CommandVolumeFocus extends CommandVolume {
    private final int volume;

    public CommandVolumeFocus(String device, int knob, int volume) {
        super(device, knob);
        this.volume = volume;
    }

    @Override
    public void execute() {
        SndCtrl.setFocusVolume(volume / 100f);
    }
}
