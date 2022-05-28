package com.getpcpanel.commands.command;

import com.getpcpanel.voicemeeter.Voicemeeter;

import lombok.Getter;

@Getter
public class CommandVoiceMeeterBasic extends CommandVoiceMeeter {
    private final Voicemeeter.ControlType ct;
    private final int index;
    private final Voicemeeter.DialType dt;
    private final int level;

    public CommandVoiceMeeterBasic(String device, int knob, Voicemeeter.ControlType ct, int index, Voicemeeter.DialType dt, int level) {
        super(device, knob);
        this.ct = ct;
        this.index = index;
        this.dt = dt;
        this.level = level;
    }

    @Override
    public void execute() {
        Voicemeeter.controlLevel(ct, index, dt, level);
    }
}
