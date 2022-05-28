package com.getpcpanel.commands.command;

import com.getpcpanel.voicemeeter.Voicemeeter;

import lombok.Getter;

@Getter
public class CommandVoiceMeeterAdvanced extends CommandVoiceMeeter {
    private final String fullParam;
    private final Voicemeeter.DialControlMode ct;
    private final int level;

    public CommandVoiceMeeterAdvanced(String device, int knob, String fullParam, Voicemeeter.DialControlMode ct, int level) {
        super(device, knob);
        this.fullParam = fullParam;
        this.ct = ct;
        this.level = level;
    }

    @Override
    public void execute() {
        Voicemeeter.controlLevel(fullParam, ct, level);
    }
}
