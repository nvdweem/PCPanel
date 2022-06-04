package com.getpcpanel.commands.command;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.util.Util;

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
        var file = new File(shortcut);
        try {
            if (file.isFile() && Util.isFileExecutable(file)) {
                rt.exec("cmd.exe /c \"" + file.getName() + "\"", null, file.getParentFile());
            } else {
                rt.exec("cmd.exe /c \"" + shortcut + "\"");
            }
        } catch (IOException e) {
            log.error("Unable to run {}", shortcut, e);
        }
    }
}
