package com.getpcpanel.ui.command.dial;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandObsSetSourceVolume;
import com.getpcpanel.obs.OBS;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.CommandContext;
import com.getpcpanel.ui.command.DialCommandController;
import com.getpcpanel.ui.command.ObsEnabled;

import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
@Cmd(name = "OBS", fxml = "Obs", cmds = CommandObsSetSourceVolume.class, enabled = ObsEnabled.class)
public class DialObsController implements DialCommandController<CommandObsSetSourceVolume> {
    private final OBS obs;

    @FXML private ChoiceBox<String> obsAudioSources;

    @Override
    public void postInit(CommandContext context) {
        if (obs.isConnected()) {
            var sourcesWithAudio = obs.getSourcesWithAudio();
            obsAudioSources.getItems().addAll(sourcesWithAudio);
        }
    }

    @Override
    public void initFromCommand(CommandObsSetSourceVolume cmd) {
        if (!obs.isConnected()) {
            obsAudioSources.getItems().add(cmd.getSourceName());
        }
        obsAudioSources.getSelectionModel().select(cmd.getSourceName());
    }

    @Override
    public Command buildCommand(boolean invert) {
        return new CommandObsSetSourceVolume(obsAudioSources.getSelectionModel().getSelectedItem(), invert);
    }
}
