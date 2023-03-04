package com.getpcpanel.ui.command.dial;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandBrightness;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.CommandContext;
import com.getpcpanel.ui.command.DialCommandController;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
@Cmd(name = "Device Brightness", fxml = "Brightness", cmds = CommandBrightness.class)
public class DialBrightnessController implements DialCommandController<CommandBrightness> {
    @Override
    public void postInit(CommandContext context) {
    }

    @Override
    public void initFromCommand(CommandBrightness cmd) {
    }

    @Override
    public Command buildCommand(boolean invert) {
        return new CommandBrightness(invert);
    }
}
