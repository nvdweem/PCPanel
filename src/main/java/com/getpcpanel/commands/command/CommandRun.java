package com.getpcpanel.commands.command;

import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

@Getter
@Log4j2
@ToString(callSuper = true)
public class CommandRun extends Command implements ButtonAction {
    private static final Runtime rt = Runtime.getRuntime();
    private final String command;

    @JsonCreator
    public CommandRun(@JsonProperty("command") String command) {
        this.command = command;
    }

    @Override
    public void execute() {
        try {
            var p = rt.exec(command);
            p.waitFor(1L, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Unable to wait for '{}' to load", command, e);
        }
    }
}
