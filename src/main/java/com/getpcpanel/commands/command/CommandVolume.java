package com.getpcpanel.commands.command;

public abstract class CommandVolume extends Command {
    protected CommandVolume(String device, int knob) {
        super(device, knob);
    }
}
