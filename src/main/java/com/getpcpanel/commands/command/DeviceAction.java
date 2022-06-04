package com.getpcpanel.commands.command;

public interface DeviceAction {
    void execute(String device);

    default Runnable toRunnable(String device) {
        return () -> execute(device);
    }
}
