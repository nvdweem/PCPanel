package com.getpcpanel.commands.command;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.cpp.SndCtrl;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Getter
@Log4j2
public class CommandEndProgram extends Command {
    private static final Runtime rt = Runtime.getRuntime();
    private final boolean specific;
    private final String name;

    public CommandEndProgram(String device, int knob, boolean specific, String name) {
        super(device, knob);
        this.specific = specific;
        this.name = name;
    }

    @Override
    public void execute() {
        var toKill = specific ? name : StringUtils.substringAfterLast(SndCtrl.getFocusApplication(), "\\");
        try {
            rt.exec("cmd.exe /c taskkill /IM " + toKill + " /F");
        } catch (IOException e) {
            log.error("Unable to end '{}'", toKill, e);
        }
    }
}
