package com.getpcpanel.commands.command;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public abstract class Command {
    private final String device;
    private final int knob;

    public abstract void execute();
}
