package com.getpcpanel.commands.command;

import lombok.ToString;

@ToString(callSuper = true)
public final class CommandNoOp extends Command implements ButtonAction, DialAction {
    public static final CommandNoOp NOOP = new CommandNoOp();

    public CommandNoOp() {
    }

    @Override
    public void execute() {
    }

    @Override
    public void execute(int value) {
    }

    @Override
    public Runnable toRunnable() {
        return ButtonAction.super.toRunnable();
    }
}