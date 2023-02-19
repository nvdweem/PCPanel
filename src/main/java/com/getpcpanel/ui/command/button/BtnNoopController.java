package com.getpcpanel.ui.command.button;

import static com.getpcpanel.ui.command.Cmd.Type.button;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandNoOp;
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
@Cmd(name = "No action", type = button, fxml = "Noop", cmds = CommandNoOp.class)
public class BtnNoopController implements CommandController<CommandNoOp> {
    @Override
    public void postInit(CommandContext context) {
    }

    @Override
    public void initFromCommand(CommandNoOp cmd) {
    }

    @Override
    public Command buildCommand() {
        return CommandNoOp.NOOP;
    }
}
