package com.getpcpanel.integration.display.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.command.ButtonAction;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.meta.CommandCategory;
import com.getpcpanel.commands.meta.CommandKind;
import com.getpcpanel.commands.meta.CommandMeta;
import com.getpcpanel.integration.display.DisplayPower;
import com.getpcpanel.util.CdiHelper;

import lombok.ToString;

/** Button action that puts the displays to sleep; moving the mouse or pressing a key wakes them. */
@ToString(callSuper = true)
@JsonTypeName("display.off")
@CommandMeta(label = "Turn displays off", category = CommandCategory.system, kinds = {CommandKind.button}, icon = "monitor")
public class CommandDisplaysOff extends Command implements ButtonAction {
    @JsonCreator
    public CommandDisplaysOff() {
    }

    @Override
    public void execute() {
        CdiHelper.getBean(DisplayPower.class).turnOff();
    }

    @Override
    public String buildLabel() {
        return "Displays off";
    }
}
