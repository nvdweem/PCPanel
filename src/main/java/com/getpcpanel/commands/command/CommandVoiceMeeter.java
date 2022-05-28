package com.getpcpanel.commands.command;

import lombok.Getter;

@Getter
public abstract class CommandVoiceMeeter extends Command {
    protected CommandVoiceMeeter(String device, int knob) {
        super(device, knob);
    }
}
