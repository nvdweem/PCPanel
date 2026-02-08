package com.getpcpanel.ui.command.dial;

import static com.getpcpanel.commands.command.CommandNoOp.NOOP;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandVoiceMeeter;
import com.getpcpanel.commands.command.CommandVoiceMeeterAdvanced;
import com.getpcpanel.commands.command.CommandVoiceMeeterBasic;
import com.getpcpanel.commands.command.DialAction.DialCommandParams;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.CommandContext;
import com.getpcpanel.ui.command.CommandController;
import com.getpcpanel.ui.command.DialCommandController;
import com.getpcpanel.ui.command.VoiceMeeterEnabled;
import com.getpcpanel.util.Util;
import com.getpcpanel.voicemeeter.Voicemeeter;
import com.getpcpanel.voicemeeter.Voicemeeter.ControlType;
import com.getpcpanel.voicemeeter.Voicemeeter.DialControlMode;
import com.getpcpanel.voicemeeter.Voicemeeter.DialType;

import javafx.beans.Observable;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
@Cmd(name = "Voicemeeter", fxml = "VoiceMeeter", cmds = { CommandVoiceMeeterBasic.class, CommandVoiceMeeterAdvanced.class }, enabled = VoiceMeeterEnabled.class)
public class DialVoiceMeeterController extends CommandController<CommandVoiceMeeter> implements DialCommandController {
    private final Voicemeeter voiceMeeter;
    @FXML private ChoiceBox<Integer> voicemeeterBasicDialIndex;
    @FXML private ChoiceBox<ControlType> voicemeeterBasicDialIO;
    @FXML private ChoiceBox<DialControlMode> voicemeeterDialType;
    @FXML private ChoiceBox<DialType> voicemeeterBasicDial;
    @FXML private TabPane voicemeeterTabPaneDial;
    @FXML private TextField voicemeeterDialParameter;

    @Override
    public void postInit(CommandContext context) {
        voicemeeterDialType.getItems().addAll(DialControlMode.values());
        if (voiceMeeter.login()) {
            voicemeeterBasicDialIO.getItems().addAll(ControlType.values());
            voicemeeterBasicDialIO.valueProperty().addListener((o, oldVal, newVal) -> {
                if (newVal == null) {
                    Util.clearAndSetNull(voicemeeterBasicDialIndex);
                    return;
                }
                Util.changeItemsTo(voicemeeterBasicDialIndex, Util.numToList(voiceMeeter.getNum(newVal)), true);
            });
            voicemeeterBasicDialIndex.valueProperty().addListener((o, oldVal, newVal) -> {
                if (newVal == null) {
                    Util.clearAndSetNull(voicemeeterBasicDial);
                    return;
                }
                Util.changeItemsTo(voicemeeterBasicDial,
                        voiceMeeter.getDialTypes(voicemeeterBasicDialIO.getValue(), voicemeeterBasicDialIndex.getValue() - 1));
            });
            voicemeeterBasicDialIO.getSelectionModel().selectFirst();
            voicemeeterBasicDialIndex.getSelectionModel().selectFirst();
        }
    }

    @Override
    public void initFromCommand(CommandVoiceMeeter cmd) {
        if (cmd instanceof CommandVoiceMeeterBasic cmdBasic) {
            if (!voiceMeeter.login()) {
                voicemeeterBasicDialIO.getItems().add(cmdBasic.getCt());
                voicemeeterBasicDialIndex.getItems().add(cmdBasic.getIndex() + 1);
                voicemeeterBasicDial.getItems().add(cmdBasic.getDt());
            }
            voicemeeterTabPaneDial.getSelectionModel().select(0);
            voicemeeterBasicDialIO.setValue(cmdBasic.getCt());
            voicemeeterBasicDialIndex.setValue(cmdBasic.getIndex() + 1);
            voicemeeterBasicDial.setValue(cmdBasic.getDt());
        } else if (cmd instanceof CommandVoiceMeeterAdvanced cmdAdvanced) {
            voicemeeterTabPaneDial.getSelectionModel().select(1);
            voicemeeterDialParameter.setText(cmdAdvanced.getFullParam());
            voicemeeterDialType.setValue(cmdAdvanced.getCt());
        }
        super.initFromCommand(cmd);
    }

    @Override
    public Command buildCommand(DialCommandParams params) {
        if (voicemeeterTabPaneDial.getSelectionModel().getSelectedIndex() == 0) {
            return new CommandVoiceMeeterBasic(voicemeeterBasicDialIO.getValue(), voicemeeterBasicDialIndex.getValue() - 1, voicemeeterBasicDial.getValue(), params);
        }
        if (voicemeeterTabPaneDial.getSelectionModel().getSelectedIndex() == 1) {
            return new CommandVoiceMeeterAdvanced(voicemeeterDialParameter.getText(), voicemeeterDialType.getValue(), params);
        }
        return NOOP;
    }

    @Override
    protected Observable[] determineDependencies() {
        return new Observable[] {
                voicemeeterTabPaneDial.getSelectionModel().selectedIndexProperty(),
                voicemeeterBasicDialIndex.valueProperty(),
                voicemeeterBasicDialIO.valueProperty(),
                voicemeeterDialType.valueProperty(),
                voicemeeterBasicDial.valueProperty(),
                voicemeeterDialParameter.textProperty()
        };
    }
}
