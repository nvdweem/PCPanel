package com.getpcpanel.ui.command.button;

import java.util.Set;

import javax.annotation.Nullable;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandEndProgram;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.PickProcessesController;
import com.getpcpanel.ui.command.ButtonCommandController;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.CommandContext;

import javafx.beans.Observable;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.RadioButton;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
@Cmd(name = "End Program", fxml = "EndProgram", cmds = CommandEndProgram.class)
public class BtnEndProgramController extends ButtonCommandController<CommandEndProgram> {
    @FXML private RadioButton rdioEndFocusedProgram;
    @FXML private RadioButton rdioEndSpecificProgram;
    @FXML private PickProcessesController applicationEndProcessController;

    @Override
    public void postInit(CommandContext context) {
        applicationEndProcessController.setPickType(PickProcessesController.PickType.process);
        applicationEndProcessController.setSingle(true);
    }

    @Override
    public void initFromCommand(CommandEndProgram endProgram) {
        if (endProgram.isSpecific()) {
            rdioEndSpecificProgram.setSelected(true);
            applicationEndProcessController.setSelection(Set.of(endProgram.getName()));
        } else {
            rdioEndFocusedProgram.setSelected(true);
        }
        onRadioButton(null);
        super.initFromCommand(endProgram);
    }

    @Override
    public Command buildCommand() {
        var selection = applicationEndProcessController.getSelection();
        var program = selection.isEmpty() ? "" : selection.get(0);
        return new CommandEndProgram(rdioEndSpecificProgram.isSelected(), program);
    }

    @Override
    protected Observable[] determineDependencies() {
        return new Observable[] { rdioEndFocusedProgram.selectedProperty(), rdioEndSpecificProgram.selectedProperty(), applicationEndProcessController.getObservable() };
    }

    @FXML
    private void onRadioButton(@Nullable ActionEvent event) {
        applicationEndProcessController.setDisable(!rdioEndSpecificProgram.isSelected());
    }
}
