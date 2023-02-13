package com.getpcpanel.ui.command.dial;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandObsSetSourceVolume;
import com.getpcpanel.obs.OBS;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.CommandContext;
import com.getpcpanel.ui.command.CommandController;

import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Tab;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
public class DialObsController implements CommandController<CommandObsSetSourceVolume> {
    private final OBS obs;

    @FXML private Tab root;
    @FXML private ChoiceBox<String> obsAudioSources;

    @Override
    public void postInit(CommandContext context, Command cmd) {
        if (obs.isConnected()) {
            var sourcesWithAudio = obs.getSourcesWithAudio();
            obsAudioSources.getItems().addAll(sourcesWithAudio);
        } else {
            if (cmd instanceof CommandObsSetSourceVolume ssv) {
                obsAudioSources.getItems().add(ssv.getSourceName());
            } else {
                root.getTabPane().getTabs().remove(root);
            }
        }
    }

    @Override
    public void initFromCommand(CommandObsSetSourceVolume cmd) {
        obsAudioSources.getSelectionModel().select(cmd.getSourceName());
    }

    @Override
    public Command buildCommand() {
        return new CommandObsSetSourceVolume(obsAudioSources.getSelectionModel().getSelectedItem());
    }
}
