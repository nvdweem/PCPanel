package com.getpcpanel.ui.command.button;

import static com.getpcpanel.commands.command.CommandNoOp.NOOP;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandVoiceMeeter;
import com.getpcpanel.commands.command.CommandVoiceMeeterAdvancedButton;
import com.getpcpanel.commands.command.CommandVoiceMeeterBasicButton;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.ButtonCommandController;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.CommandContext;
import com.getpcpanel.ui.command.CommandController;
import com.getpcpanel.ui.command.VoiceMeeterEnabled;
import com.getpcpanel.util.Util;
import com.getpcpanel.voicemeeter.Voicemeeter;
import com.getpcpanel.voicemeeter.Voicemeeter.ButtonControlMode;
import com.getpcpanel.voicemeeter.Voicemeeter.ButtonType;
import com.getpcpanel.voicemeeter.Voicemeeter.ControlType;

import javafx.beans.Observable;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
@Cmd(name = "Voicemeeter", fxml = "VoiceMeeter", cmds = { CommandVoiceMeeterBasicButton.class, CommandVoiceMeeterAdvancedButton.class }, enabled = VoiceMeeterEnabled.class)
public class BtnVoiceMeeterController extends CommandController<CommandVoiceMeeter> implements ButtonCommandController {
    private final Voicemeeter voiceMeeter;

    @FXML private ChoiceBox<Integer> voicemeeterBasicButtonIndex;
    @FXML private ChoiceBox<ButtonControlMode> voicemeeterButtonType;
    @FXML private ChoiceBox<ButtonType> voicemeeterBasicButton;
    @FXML private ChoiceBox<ControlType> voicemeeterBasicButtonIO;
    @FXML private TabPane voicemeeterTabPaneButton;
    @FXML private TextField voicemeeterButtonParameter;
    @FXML private Label voicemeeterStringValueLabel;
    @FXML private TextField voicemeeterStringValue;

    @Override
    public void postInit(CommandContext context) {
        voicemeeterButtonType.getItems().addAll(ButtonControlMode.values());
        if (voiceMeeter.login()) {
            voicemeeterBasicButtonIO.getItems().addAll(ControlType.values());
            voicemeeterBasicButtonIO.valueProperty().addListener((o, oldVal, newVal) -> {
                if (newVal == null) {
                    Util.clearAndSetNull(voicemeeterBasicButtonIndex);
                    return;
                }
                Util.changeItemsTo(voicemeeterBasicButtonIndex, Util.numToList(voiceMeeter.getNum(newVal)), true);
            });
            voicemeeterBasicButtonIndex.valueProperty().addListener((o, oldVal, newVal) -> {
                if (newVal == null) {
                    Util.clearAndSetNull(voicemeeterBasicButton);
                    return;
                }
                Util.changeItemsTo(voicemeeterBasicButton,
                        voiceMeeter.getButtonTypes(voicemeeterBasicButtonIO.getValue(), voicemeeterBasicButtonIndex.getValue() - 1));
            });
            voicemeeterBasicButtonIO.getSelectionModel().selectFirst();
            voicemeeterBasicButtonIndex.getSelectionModel().selectFirst();
        }

        voicemeeterButtonType.valueProperty().addListener((o, oldVal, newVal) -> {
            var isString = newVal == ButtonControlMode.STRING;
            voicemeeterStringValueLabel.setVisible(isString);
            voicemeeterStringValue.setVisible(isString);
            if (!isString) {
                voicemeeterStringValue.setText("");
            }
        });
    }

    @Override
    public void initFromCommand(CommandVoiceMeeter cmd) {
        if (cmd instanceof CommandVoiceMeeterBasicButton basic) {
            if (!voiceMeeter.login()) {
                voicemeeterBasicButtonIO.getItems().add(basic.getCt());
                voicemeeterBasicButtonIndex.getItems().add(basic.getIndex() + 1);
                voicemeeterBasicButton.getItems().add(basic.getBt());
            }

            voicemeeterTabPaneButton.getSelectionModel().select(0);
            voicemeeterBasicButtonIO.setValue(basic.getCt());
            voicemeeterBasicButtonIndex.setValue(basic.getIndex() + 1);
            voicemeeterBasicButton.setValue(basic.getBt());
        } else if (cmd instanceof CommandVoiceMeeterAdvancedButton advanced) {
            voicemeeterTabPaneButton.getSelectionModel().select(1);
            voicemeeterButtonParameter.setText(advanced.getFullParam());
            voicemeeterButtonType.setValue(advanced.getBt());
            if (advanced.getBt() == ButtonControlMode.STRING) {
                voicemeeterStringValue.setText(advanced.getStringValue());
            }
        }
        super.initFromCommand(cmd);
    }

    @Override
    public Command buildCommand() {
        if (voicemeeterTabPaneButton.getSelectionModel().getSelectedIndex() == 0) {
            return new CommandVoiceMeeterBasicButton(voicemeeterBasicButtonIO.getValue(), voicemeeterBasicButtonIndex.getValue() - 1, voicemeeterBasicButton.getValue());
        } else if (voicemeeterTabPaneButton.getSelectionModel().getSelectedIndex() == 1) {
            if (voicemeeterButtonType.getValue() == null) {
                return NOOP;
            }
            return new CommandVoiceMeeterAdvancedButton(voicemeeterButtonParameter.getText(), voicemeeterButtonType.getValue(), voicemeeterStringValue.getText());
        }
        return NOOP;
    }

    @Override
    protected Observable[] determineDependencies() {
        return new Observable[] { voicemeeterTabPaneButton.getSelectionModel().selectedIndexProperty(),
                voicemeeterBasicButtonIO.valueProperty(), voicemeeterBasicButtonIndex.valueProperty(), voicemeeterBasicButton.valueProperty(),
                voicemeeterButtonParameter.textProperty(), voicemeeterButtonType.valueProperty(), voicemeeterStringValue.textProperty() };
    }
}
