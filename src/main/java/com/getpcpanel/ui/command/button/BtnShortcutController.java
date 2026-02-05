package com.getpcpanel.ui.command.button;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandShortcut;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.UIHelper;
import com.getpcpanel.ui.command.ButtonCommandController;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.CommandContext;
import com.getpcpanel.ui.command.CommandController;

import javafx.beans.Observable;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
@Cmd(name = "Shortcut", fxml = "Shortcut", cmds = CommandShortcut.class)
public class BtnShortcutController extends CommandController<CommandShortcut> implements ButtonCommandController {
    @FXML private TextField shortcutField;

    @Override
    public void postInit(CommandContext context) {
    }

    @Override
    public void initFromCommand(CommandShortcut cmd) {
        shortcutField.setText(cmd.getShortcut());
        super.initFromCommand(cmd);
    }

    @Override
    public Command buildCommand() {
        return new CommandShortcut(shortcutField.getText());
    }

    @Override
    protected Observable[] determineDependencies() {
        return new Observable[] { shortcutField.textProperty() };
    }

    @FXML
    private void scFile(ActionEvent event) {
        UIHelper.showFilePicker("Pick file", shortcutField);
    }
}
