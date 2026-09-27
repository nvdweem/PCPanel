package com.getpcpanel.commands;

import com.getpcpanel.commands.command.Command;

/**
 * An integration that holds a connection to an external app. {@link IntegrationUseNotifier} tells it when
 * a control runs one of its commands, so it can reconnect right away instead of on its next scheduled
 * check (see {@link com.getpcpanel.util.concurrent.ReconnectOnUse}).
 */
public interface IntegrationConnection {
    /** Whether the command acts through this integration. */
    boolean owns(Command command);

    /** A command of this integration is about to run. Called on the input path, so it must not block. */
    void onUsed();
}
