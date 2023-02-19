package com.getpcpanel.ui.command.dial;

import static com.getpcpanel.ui.command.Cmd.Type.dial;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandVolumeFocus;
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
@Cmd(name = "Focus Volume", type = dial, fxml = "VolumeFocus", cmds = CommandVolumeFocus.class)
public class DialVolumeFocusController implements CommandController<CommandVolumeFocus> {
    @Override
    public void postInit(CommandContext context) {
    }

    @Override
    public void initFromCommand(CommandVolumeFocus cmd) {
    }

    @Override
    public Command buildCommand() {
        return new CommandVolumeFocus();
    }
}
