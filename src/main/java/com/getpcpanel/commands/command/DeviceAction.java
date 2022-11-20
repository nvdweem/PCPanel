package com.getpcpanel.commands.command;

public interface DeviceAction {
    void execute(DeviceActionParameters context);

    default Runnable toRunnable(DeviceActionParameters context) {
        return () -> execute(context);
    }

    record DeviceActionParameters(String device) {
    }
}
