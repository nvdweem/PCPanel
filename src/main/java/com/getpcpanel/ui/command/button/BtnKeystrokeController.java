package com.getpcpanel.ui.command.button;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandKeystroke;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.ButtonCommandController;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.CommandContext;
import com.getpcpanel.ui.command.CommandController;

import javafx.beans.Observable;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
@Cmd(name = "Keystroke", fxml = "Keystroke", cmds = CommandKeystroke.class)
public class BtnKeystrokeController extends CommandController<CommandKeystroke> implements ButtonCommandController {
    @FXML private TextField keystrokeField;
    private boolean k_alt;
    private boolean k_ctrl;
    private boolean k_shift;
    private boolean k_win;

    @Override
    public void postInit(CommandContext context) {
        keystrokeField.setOnKeyPressed(event -> {
            var code = event.getCode();
            if (code == KeyCode.ALT) {
                k_alt = true;
            } else if (code == KeyCode.SHIFT) {
                k_shift = true;
            } else if (code == KeyCode.WINDOWS) {
                k_win = true;
            } else if (code == KeyCode.CONTROL) {
                k_ctrl = true;
            } else if (!k_alt && !k_shift && !k_win && !k_ctrl) {
                if (code.name().startsWith("DIGIT")) {
                    keystrokeField.setText(code.name().substring(5));
                } else {
                    keystrokeField.setText(code.name());
                }
            } else {
                var str = new StringBuilder();
                var bools = new boolean[] { k_ctrl, k_shift, k_alt, k_win };
                var keys = new String[] { "ctrl", "shift", "alt", "windows" };
                for (var i = 0; i < 4; i++) {
                    if (bools[i])
                        str.append(keys[i]).append(" + ");
                }
                if (code.name().startsWith("DIGIT")) {
                    str.append(code.name().substring(5));
                } else {
                    str.append(code.name());
                }
                keystrokeField.setText(str.toString());
            }
        });
        keystrokeField.setOnKeyReleased(event -> {
            var code = event.getCode();
            if (code == KeyCode.ALT) {
                k_alt = false;
            } else if (code == KeyCode.SHIFT) {
                k_shift = false;
            } else if (code == KeyCode.WINDOWS) {
                k_win = false;
            } else if (code == KeyCode.CONTROL) {
                k_ctrl = false;
            }
        });
    }

    @Override
    public void initFromCommand(CommandKeystroke cmd) {
        keystrokeField.setText(cmd.getKeystroke());
        super.initFromCommand(cmd);
    }

    @Override
    public Command buildCommand() {
        return new CommandKeystroke(keystrokeField.getText());
    }

    @Override
    protected Observable[] determineDependencies() {
        return new Observable[] { keystrokeField.textProperty() };
    }

    @FXML
    private void clearKeystroke(ActionEvent event) {
        keystrokeField.setText("");
        k_alt = false;
        k_shift = false;
        k_win = false;
        k_ctrl = false;
    }
}
