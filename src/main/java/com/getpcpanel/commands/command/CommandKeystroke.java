package com.getpcpanel.commands.command;

import com.getpcpanel.commands.KeyMacro;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

@Getter
@Log4j2
@ToString(callSuper = true)
public class CommandKeystroke extends Command implements ButtonAction {
    private final String keystroke;

    public CommandKeystroke(String keystroke) {
        this.keystroke = keystroke;
    }

    @Override
    public void execute() {
        KeyMacro.executeKeyStroke(keystroke);
    }
}
