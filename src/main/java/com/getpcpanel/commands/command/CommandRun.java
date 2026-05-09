package com.getpcpanel.commands.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.util.CdiHelper;
import com.getpcpanel.util.IPlatformCommand;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

@Getter
@Log4j2
@ToString(callSuper = true)
public class CommandRun extends Command implements ButtonAction {
    private final String command;

    @JsonCreator
    public CommandRun(@JsonProperty("command") String command) {
        this.command = command;
    }

    @Override
    public void execute() {
        CdiHelper.getBean(IPlatformCommand.class).exec(command);
    }

    @Override
    public String buildLabel() {
        return command;
    }
}
