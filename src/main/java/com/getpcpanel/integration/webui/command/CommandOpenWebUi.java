package com.getpcpanel.integration.webui.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.command.ButtonAction;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.meta.CommandCategory;
import com.getpcpanel.commands.meta.CommandKind;
import com.getpcpanel.commands.meta.CommandMeta;
import com.getpcpanel.util.app.AppEvents;
import com.getpcpanel.util.app.ShowMainEvent;

import lombok.ToString;

/**
 * Button action that opens the PCPanel web UI in the browser, by firing the same {@link ShowMainEvent}
 * the tray "Open PCPanel" action uses. This is the on-demand way to open the UI on any platform — notably
 * macOS, which has no tray icon.
 */
@ToString(callSuper = true)
@JsonTypeName("webui.open")
@CommandMeta(label = "Open web UI", category = CommandCategory.system, kinds = {CommandKind.button}, icon = "window")
public class CommandOpenWebUi extends Command implements ButtonAction {
    @JsonCreator
    public CommandOpenWebUi() {
    }

    @Override
    public void execute() {
        AppEvents.fire(new ShowMainEvent());
    }

    @Override
    public String buildLabel() {
        return "Open web UI";
    }
}
