package com.getpcpanel.integration.program.command;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.ButtonAction;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.meta.CommandCategory;
import com.getpcpanel.commands.meta.CommandKind;
import com.getpcpanel.commands.meta.CommandMeta;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.util.CdiHelper;
import com.getpcpanel.integration.program.IPlatformCommand;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

@Getter
@Log4j2
@ToString(callSuper = true)
@JsonTypeName("program.shortcut")
@CommandMeta(label = "Run shortcut", category = CommandCategory.system, kinds = {CommandKind.button}, icon = "zap", legacyIds = {"com.getpcpanel.commands.command.CommandShortcut"})
public class CommandShortcut extends Command implements ButtonAction {
    private static final Runtime rt = Runtime.getRuntime();
    private final String shortcut;

    @JsonCreator
    public CommandShortcut(@JsonProperty("shortcut") String shortcut) {
        this.shortcut = shortcut;
    }

    @Override
    public void execute() {
        CdiHelper.getBean(IPlatformCommand.class).exec(shortcut);
    }

    @Override
    public String buildLabel() {
        return shortcut;
    }
}
