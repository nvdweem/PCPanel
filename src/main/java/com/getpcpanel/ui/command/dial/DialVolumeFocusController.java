package com.getpcpanel.ui.command.dial;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandVolumeFocus;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.CommandContext;
import com.getpcpanel.ui.command.DialCommandController;

import javafx.beans.Observable;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
@Cmd(name = "Focus Volume", fxml = "VolumeFocus", cmds = CommandVolumeFocus.class)
public class DialVolumeFocusController extends DialCommandController<CommandVolumeFocus> {

    @Override
    public void postInit(CommandContext context) {
    }

    @Override
    public Command buildCommand(boolean invert) {
        return new CommandVolumeFocus(invert);
    }

    @Override
    protected Observable[] determineDependencies() {
        return new Observable[0];
    }
}
