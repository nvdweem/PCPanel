package com.getpcpanel.commands.command;

import lombok.Getter;

@Getter
public abstract class CommandObs extends Command {
    protected CommandObs(String device, int knob) {
        super(device, knob);
    }
}
