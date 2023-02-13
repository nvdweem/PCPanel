package com.getpcpanel.ui.command.dial;

import static com.getpcpanel.commands.command.CommandNoOp.NOOP;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandVoiceMeeter;
import com.getpcpanel.commands.command.CommandVoiceMeeterAdvanced;
import com.getpcpanel.commands.command.CommandVoiceMeeterBasic;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.CommandContext;
import com.getpcpanel.ui.command.CommandController;
import com.getpcpanel.util.Util;
import com.getpcpanel.voicemeeter.Voicemeeter;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
public class DialVoiceMeeterController implements CommandController<CommandVoiceMeeter> {
    public static final String MUST_SELECT_A_CONTROL_TYPE_MESSAGE = "Must Select a Control Type";

    private final Voicemeeter voiceMeeter;
    private Stage stage;
    @FXML private Tab root;

    @FXML private ChoiceBox<Integer> voicemeeterBasicDialIndex;
    @FXML private ChoiceBox<Voicemeeter.ControlType> voicemeeterBasicDialIO;
    @FXML private ChoiceBox<Voicemeeter.DialControlMode> voicemeeterDialType;
    @FXML private ChoiceBox<Voicemeeter.DialType> voicemeeterBasicDial;
    @FXML private TabPane voicemeeterTabPaneDial;
    @FXML private TextField voicemeeterDialParameter;

    @Override
    public void postInit(CommandContext context, Command cmd) {
        stage = context.stage();
        voicemeeterDialType.getItems().addAll(Voicemeeter.DialControlMode.values());
        if (voiceMeeter.login()) {
            voicemeeterBasicDialIO.getItems().addAll(Voicemeeter.ControlType.values());
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
        } else {
            if (cmd instanceof CommandVoiceMeeter) {
                if (cmd instanceof CommandVoiceMeeterBasic vmb) {
                    voicemeeterBasicDialIO.getItems().add(vmb.getCt());
                    voicemeeterBasicDialIndex.getItems().add(vmb.getIndex() + 1);
                    voicemeeterBasicDial.getItems().add(vmb.getDt());
                }
            } else {
                root.getTabPane().getTabs().remove(root);
            }
        }
    }

    @Override
    public void initFromCommand(CommandVoiceMeeter cmd) {
        if (cmd instanceof CommandVoiceMeeterBasic cmdBasic) {
            voicemeeterTabPaneDial.getSelectionModel().select(0);
            voicemeeterBasicDialIO.setValue(cmdBasic.getCt());
            voicemeeterBasicDialIndex.setValue(cmdBasic.getIndex() + 1);
            voicemeeterBasicDial.setValue(cmdBasic.getDt());
        } else if (cmd instanceof CommandVoiceMeeterAdvanced cmdAdvanced) {
            voicemeeterTabPaneDial.getSelectionModel().select(1);
            voicemeeterDialParameter.setText(cmdAdvanced.getFullParam());
            voicemeeterDialType.setValue(cmdAdvanced.getCt());
        }
    }

    @Override
    public Command buildCommand() {
        if (voicemeeterTabPaneDial.getSelectionModel().getSelectedIndex() == 0) {
            return new CommandVoiceMeeterBasic(voicemeeterBasicDialIO.getValue(), voicemeeterBasicDialIndex.getValue() - 1, voicemeeterBasicDial.getValue());
        }
        if (voicemeeterTabPaneDial.getSelectionModel().getSelectedIndex() == 1) {
            if (voicemeeterDialType.getValue() == null) {
                showControlTypeError();
                return NOOP;
            }
            return new CommandVoiceMeeterAdvanced(voicemeeterDialParameter.getText(), voicemeeterDialType.getValue());
        }
        return NOOP;
    }

    private void showControlTypeError() {
        var a = new Alert(Alert.AlertType.ERROR, MUST_SELECT_A_CONTROL_TYPE_MESSAGE);
        a.initOwner(stage);
        a.show();
    }
}
