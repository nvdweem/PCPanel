package com.getpcpanel.commands.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.MainFX;
import com.getpcpanel.util.IPlatformCommand;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

@Getter
@Log4j2
@ToString(callSuper = true)
public class CommandEndProgram extends Command implements ButtonAction {
    private static final Runtime rt = Runtime.getRuntime();
    private final boolean specific;
    private final String name;

    @JsonCreator
    public CommandEndProgram(@JsonProperty("specific") boolean specific, @JsonProperty("name") String name) {
        this.specific = specific;
        this.name = name;
    }

    @Override
    public void execute() {
        MainFX.getBean(IPlatformCommand.class).kill(specific ? name : IPlatformCommand.FOCUS);
    }
}
