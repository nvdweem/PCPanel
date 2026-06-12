package com.getpcpanel.ui.command.button;

import org.apache.commons.lang3.SystemUtils;
import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandKeystroke;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.ButtonCommandController;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.CommandContext;
import com.getpcpanel.ui.command.CommandController;
import com.getpcpanel.util.OsxPermissionHelper;

import javafx.beans.Observable;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
@Cmd(name = "Keystroke", fxml = "Keystroke", cmds = CommandKeystroke.class)
public class BtnKeystrokeController extends CommandController<CommandKeystroke> implements ButtonCommandController {
    private static final String ACCESSIBILITY_WARNING = "Keystrokes require Accessibility permission: System Settings > Privacy & Security > Accessibility > enable PCPanel";
    @FXML private TextField keystrokeField;
    private boolean k_alt;
    private boolean k_ctrl;
    private boolean k_shift;
    private boolean k_win;
    private boolean k_cmd;

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
            } else if (code == KeyCode.COMMAND || code == KeyCode.META) {
                k_cmd = true;
            } else if (!k_alt && !k_shift && !k_win && !k_ctrl && !k_cmd) {
                if (code.name().startsWith("DIGIT")) {
                    keystrokeField.setText(code.name().substring(5));
                } else {
                    keystrokeField.setText(code.name());
                }
            } else {
                var str = new StringBuilder();
                var bools = new boolean[] { k_ctrl, k_shift, k_alt, k_win, k_cmd };
                var keys = new String[] { "ctrl", "shift", "alt", "windows", "cmd" };
                for (var i = 0; i < bools.length; i++) {
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
            } else if (code == KeyCode.COMMAND || code == KeyCode.META) {
                k_cmd = false;
            }
        });
        warnIfAccessibilityNotGranted();
    }

    private void warnIfAccessibilityNotGranted() {
        if (!SystemUtils.IS_OS_MAC || OsxPermissionHelper.isAccessibilityGranted()) {
            return;
        }
        // The fxml root is an HBox > VBox > (Label, HBox(keystrokeField, clear button)), add the warning at the bottom of the VBox
        if (keystrokeField.getParent() != null && keystrokeField.getParent().getParent() instanceof Pane container) {
            var warning = new Label(ACCESSIBILITY_WARNING);
            warning.setWrapText(true);
            container.getChildren().add(warning);
        } else {
            keystrokeField.setPromptText("Accessibility permission required");
            keystrokeField.setTooltip(new Tooltip(ACCESSIBILITY_WARNING));
        }
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
        k_cmd = false;
    }
}
