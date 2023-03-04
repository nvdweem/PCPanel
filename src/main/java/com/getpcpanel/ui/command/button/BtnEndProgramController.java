package com.getpcpanel.ui.command.button;

import javax.annotation.Nullable;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandEndProgram;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.FxHelper;
import com.getpcpanel.ui.command.ButtonCommandController;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.CommandContext;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
@Cmd(name = "End Program", fxml = "EndProgram", cmds = CommandEndProgram.class)
public class BtnEndProgramController implements ButtonCommandController<CommandEndProgram> {
    private final FxHelper fxHelper;
    private Stage stage;

    @FXML private Button findAppEndProcess;
    @FXML private RadioButton rdioEndFocusedProgram;
    @FXML private RadioButton rdioEndSpecificProgram;
    @FXML private TextField endProcessField;

    @Override
    public void postInit(CommandContext context) {
        stage = context.stage();
    }

    @Override
    public void initFromCommand(CommandEndProgram endProgram) {
        if (endProgram.isSpecific()) {
            rdioEndSpecificProgram.setSelected(true);
            endProcessField.setText(endProgram.getName());
        } else {
            rdioEndFocusedProgram.setSelected(true);
        }
        onRadioButton(null);
    }

    @Override
    public Command buildCommand() {
        return new CommandEndProgram(rdioEndSpecificProgram.isSelected(), endProcessField.getText());
    }

    @FXML
    private void findApps(ActionEvent event) {
        TextField processTextField;
        var button = (Button) event.getSource();
        var id = button.getId();
        var afd = fxHelper.buildAppFinderDialog(stage, !"findAppEndProcess".equals(id));
        var afdStage = new Stage();
        afd.start(afdStage);
        var processNameResult = afd.getProcessName();
        if (processNameResult == null || id == null)
            return;
        if ("findAppEndProcess".equals(id)) {
            processTextField = endProcessField;
        } else {
            log.error("invalid findApp button");
            return;
        }
        processTextField.setText(processNameResult);
    }

    @FXML
    private void onRadioButton(@Nullable ActionEvent event) {
        if (rdioEndSpecificProgram.isSelected()) {
            endProcessField.setDisable(false);
            findAppEndProcess.setDisable(false);
        } else {
            endProcessField.setDisable(true);
            findAppEndProcess.setDisable(true);
        }
    }
}
