package com.getpcpanel.ui.command.dial;

import static com.getpcpanel.ui.command.Cmd.Type.dial;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandBrightness;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.CommandContext;
import com.getpcpanel.ui.command.CommandController;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
@Cmd(name = "Device Brightness", type = dial, fxml = "Brightness", cmds = CommandBrightness.class)
public class DialBrightnessController implements CommandController<CommandBrightness> {
    @Override
    public void postInit(CommandContext context) {
    }

    @Override
    public void initFromCommand(CommandBrightness cmd) {
    }

    @Override
    public Command buildCommand() {
        return new CommandBrightness();
    }
}
