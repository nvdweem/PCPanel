package com.getpcpanel.commands.command;

import lombok.ToString;

@ToString(callSuper = true)
public final class CommandNoOp extends Command implements ButtonAction, DialAction {
    public static final CommandNoOp NOOP = new CommandNoOp();

    public static boolean isNoOp(Command cmd) {
        return cmd == null || cmd instanceof CommandNoOp;
    }

    @Override
    public void execute() {
    }

    @Override
    public void execute(DialActionParameters context) {
    }

    @Override
    public Runnable toRunnable() {
        return ButtonAction.super.toRunnable();
    }

    @Override
    public boolean hasOverlay() {
        return false;
    }
}
