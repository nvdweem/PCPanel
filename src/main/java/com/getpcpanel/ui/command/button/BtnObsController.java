package com.getpcpanel.ui.command.button;

import static com.getpcpanel.commands.command.CommandNoOp.NOOP;

import javax.annotation.Nullable;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandObs;
import com.getpcpanel.commands.command.CommandObsMuteSource;
import com.getpcpanel.commands.command.CommandObsSetScene;
import com.getpcpanel.obs.OBS;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.CommandContext;
import com.getpcpanel.ui.command.CommandController;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Tab;
import javafx.scene.layout.Pane;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
public class BtnObsController implements CommandController<CommandObs> {
    private final OBS obs;
    @FXML private Tab root;

    @FXML private ChoiceBox<String> obsSetScene;
    @FXML private ChoiceBox<String> obsSourceToMute;
    @FXML private Pane obsPaneMuteSource;
    @FXML private Pane obsPaneSetScene;
    @FXML private RadioButton obsMuteMute;
    @FXML private RadioButton obsMuteToggle;
    @FXML private RadioButton obsMuteUnmute;
    @FXML private RadioButton obs_rdio_MuteSource;
    @FXML private RadioButton obs_rdio_SetScene;

    @Override
    public void postInit(CommandContext context, Command cmd) {
        if (obs.isConnected()) {
            var sourcesWithAudio = obs.getSourcesWithAudio();
            var scenes = obs.getScenes();
            obsSourceToMute.getItems().addAll(sourcesWithAudio);
            obsSetScene.getItems().addAll(scenes);
        } else {
            if (cmd instanceof CommandObsMuteSource ms) {
                obsSourceToMute.getItems().add(ms.getSource());
            } else if (cmd instanceof CommandObsSetScene ss) {
                obsSetScene.getItems().add(ss.getScene());
            } else {
                root.getTabPane().getTabs().remove(root);
            }
        }
    }

    @Override
    public void initFromCommand(CommandObs cmd) {
        if (cmd instanceof CommandObsSetScene ss) {
            obs_rdio_SetScene.setSelected(true);
            obsSetScene.getSelectionModel().select(ss.getScene());
        } else if (cmd instanceof CommandObsMuteSource ms) {
            obs_rdio_MuteSource.setSelected(true);
            obsSourceToMute.getSelectionModel().select(ms.getSource());
            switch (ms.getType()) {
                case unmute -> obsMuteUnmute.setSelected(true);
                case mute -> obsMuteMute.setSelected(true);
                case toggle -> obsMuteToggle.setSelected(true);
            }
        }
        onRadioButton(null);
    }

    @Override
    public Command buildCommand() {
        if (obs_rdio_SetScene.isSelected()) {
            return new CommandObsSetScene(obsSetScene.getSelectionModel().getSelectedItem());
        } else if (obs_rdio_MuteSource.isSelected()) {
            return new CommandObsMuteSource(obsSourceToMute.getSelectionModel().getSelectedItem(),
                    obsMuteUnmute.isSelected() ? CommandObsMuteSource.MuteType.unmute : obsMuteMute.isSelected() ? CommandObsMuteSource.MuteType.mute : CommandObsMuteSource.MuteType.toggle);
        } else {
            log.error("ERROR INVALID RADIO BUTTON IN BUTTON OBS");
            return NOOP;
        }
    }

    @FXML
    private void onRadioButton(@Nullable ActionEvent event) {
        obsPaneSetScene.setDisable(!obs_rdio_SetScene.isSelected());
        obsPaneMuteSource.setDisable(!obs_rdio_MuteSource.isSelected());
    }
}
