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
public class CommandShortcut extends Command implements ButtonAction {
    private static final Runtime rt = Runtime.getRuntime();
    private final String shortcut;

    @JsonCreator
    public CommandShortcut(@JsonProperty("shortcut") String shortcut) {
        this.shortcut = shortcut;
    }

    @Override
    public void execute() {
        MainFX.getBean(IPlatformCommand.class).exec(shortcut);
    }
}
