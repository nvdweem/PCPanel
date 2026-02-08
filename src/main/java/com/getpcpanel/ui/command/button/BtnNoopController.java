package com.getpcpanel.ui.command.button;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandNoOp;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.ButtonCommandController;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.CommandContext;
import com.getpcpanel.ui.command.CommandController;

import javafx.beans.Observable;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
@Cmd(name = "No action", fxml = "Noop", cmds = CommandNoOp.class)
public class BtnNoopController extends CommandController<CommandNoOp> implements ButtonCommandController {
    @Override
    public void postInit(CommandContext context) {
    }

    @Override
    public Command buildCommand() {
        return CommandNoOp.NOOP;
    }

    @Override
    protected Observable[] determineDependencies() {
        return new Observable[0];
    }
}
