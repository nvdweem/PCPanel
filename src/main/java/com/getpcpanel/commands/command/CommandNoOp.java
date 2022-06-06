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
    public void execute(boolean initial, int value) {
    }

    @Override
    @SuppressWarnings("RedundantMethodOverride") // Comes from interface so we need to pick which parent
    public Runnable toRunnable() {
        return ButtonAction.super.toRunnable();
    }
}
