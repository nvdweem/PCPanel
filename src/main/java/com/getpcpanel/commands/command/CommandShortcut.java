package com.getpcpanel.commands.command;

import java.io.File;
import java.io.IOException;

import com.getpcpanel.util.Util;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Getter
@Log4j2
public class CommandShortcut extends Command {
    private static final Runtime rt = Runtime.getRuntime();
    private final String shortcut;

    public CommandShortcut(String device, int knob, String shortcut) {
        super(device, knob);
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
