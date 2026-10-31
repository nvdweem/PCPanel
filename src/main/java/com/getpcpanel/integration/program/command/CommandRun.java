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
import com.getpcpanel.template.TemplateContext;
import com.getpcpanel.template.Templates;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

/**
 * Runs a program or command line. The command is a template, so run from a dial (as an action of
 * {@code Action on change}) it can pass the position on, e.g. <code>--Set={{ percent }}</code>.
 */
@Getter
@Log4j2
@ToString(callSuper = true)
@JsonTypeName("program.run")
@CommandMeta(label = "Run command", category = CommandCategory.system, kinds = {CommandKind.button}, icon = "zap", legacyIds = {"com.getpcpanel.commands.command.CommandRun"})
public class CommandRun extends Command implements ButtonAction {
    private final String command;

    @JsonCreator
    public CommandRun(@JsonProperty("command") String command) {
        this.command = command;
    }

    @Override
    public void execute() {
        CdiHelper.getBean(IPlatformCommand.class).exec(Templates.render(command, TemplateContext.current()));
    }

    @Override
    public String buildLabel() {
        return command;
    }
}
