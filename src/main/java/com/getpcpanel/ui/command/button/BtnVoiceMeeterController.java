package com.getpcpanel.ui.command.button;

import static com.getpcpanel.commands.command.CommandNoOp.NOOP;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandVoiceMeeter;
import com.getpcpanel.commands.command.CommandVoiceMeeterAdvancedButton;
import com.getpcpanel.commands.command.CommandVoiceMeeterBasicButton;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.CommandContext;
import com.getpcpanel.ui.command.CommandController;
import com.getpcpanel.util.Util;
import com.getpcpanel.voicemeeter.Voicemeeter;

import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
public class BtnVoiceMeeterController implements CommandController<CommandVoiceMeeter> {
    private final Voicemeeter voiceMeeter;
    @FXML private Tab root;

    @FXML private ChoiceBox<Integer> voicemeeterBasicButtonIndex;
    @FXML private ChoiceBox<Voicemeeter.ButtonControlMode> voicemeeterButtonType;
    @FXML private ChoiceBox<Voicemeeter.ButtonType> voicemeeterBasicButton;
    @FXML private ChoiceBox<Voicemeeter.ControlType> voicemeeterBasicButtonIO;
    @FXML private TabPane voicemeeterTabPaneButton;
    @FXML private TextField voicemeeterButtonParameter;

    @Override
    public void postInit(CommandContext context, Command cmd) {
        voicemeeterButtonType.getItems().addAll(Voicemeeter.ButtonControlMode.values());
        if (voiceMeeter.login()) {
            voicemeeterBasicButtonIO.getItems().addAll(Voicemeeter.ControlType.values());
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
        } else {
            if (cmd instanceof CommandVoiceMeeter) {
                if (cmd instanceof CommandVoiceMeeterBasicButton vmb) {
                    voicemeeterBasicButtonIO.getItems().add(vmb.getCt());
                    voicemeeterBasicButtonIndex.getItems().add(vmb.getIndex() + 1);
                    voicemeeterBasicButton.getItems().add(vmb.getBt());
                }
            } else {
                root.getTabPane().getTabs().remove(root);
            }
        }
    }

    @Override
    public void initFromCommand(CommandVoiceMeeter cmd) {
        if (cmd instanceof CommandVoiceMeeterBasicButton basic) {
            voicemeeterTabPaneButton.getSelectionModel().select(0);
            voicemeeterBasicButtonIO.setValue(basic.getCt());
            voicemeeterBasicButtonIndex.setValue(basic.getIndex() + 1);
            voicemeeterBasicButton.setValue(basic.getBt());
        } else if (cmd instanceof CommandVoiceMeeterAdvancedButton advanced) {
            voicemeeterTabPaneButton.getSelectionModel().select(1);
            voicemeeterButtonParameter.setText(advanced.getFullParam());
            voicemeeterButtonType.setValue(advanced.getBt());
        }

    }

    @Override
    public Command buildCommand() {
        if (voicemeeterTabPaneButton.getSelectionModel().getSelectedIndex() == 0) {
            return new CommandVoiceMeeterBasicButton(voicemeeterBasicButtonIO.getValue(), voicemeeterBasicButtonIndex.getValue() - 1, voicemeeterBasicButton.getValue());
        } else if (voicemeeterTabPaneButton.getSelectionModel().getSelectedIndex() == 1) {
            if (voicemeeterButtonType.getValue() == null) {
                return NOOP;
            }
            return new CommandVoiceMeeterAdvancedButton(voicemeeterButtonParameter.getText(), voicemeeterButtonType.getValue());
        }
        return NOOP;
    }
}
