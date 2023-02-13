package com.getpcpanel.ui.command.button;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandShortcut;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.UIHelper;
import com.getpcpanel.ui.command.CommandContext;
import com.getpcpanel.ui.command.CommandController;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
public class BtnShortcutController implements CommandController<CommandShortcut> {
    @FXML private TextField shortcutField;

    @Override
    public void postInit(CommandContext context, Command cmd) {
    }

    @Override
    public void initFromCommand(CommandShortcut cmd) {
        shortcutField.setText(cmd.getShortcut());
    }

    @Override
    public Command buildCommand() {
        return new CommandShortcut(shortcutField.getText());
    }

    @FXML
    private void scFile(ActionEvent event) {
        UIHelper.showFilePicker("Pick file", shortcutField);
    }
}
