package com.getpcpanel.ui.command.button;

import static com.getpcpanel.spring.OsHelper.MAC;
import static com.getpcpanel.spring.OsHelper.WINDOWS;

import org.apache.commons.lang3.SystemUtils;
import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandMedia;
import com.getpcpanel.commands.command.CommandMedia.VolumeButton;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.ButtonCommandController;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.CommandContext;
import com.getpcpanel.ui.command.CommandController;

import javafx.beans.Observable;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
@Cmd(name = "Music Control", fxml = "Media", cmds = CommandMedia.class, os = { WINDOWS, MAC })
public class BtnMediaController extends CommandController<CommandMedia> implements ButtonCommandController {
    @FXML private ToggleGroup mediagroup;
    @FXML private RadioButton mute;
    @FXML private CheckBox cmdMediaSpotify;
    @FXML private Label spotifyDescription;

    @Override
    public void postInit(CommandContext context) {
        if (SystemUtils.IS_OS_MAC) {
            spotifyDescription.setText("Check this box to control Spotify. If this is not checked, the Music app is controlled.");
            mute.setVisible(false); // OsxMediaControl has no mute equivalent
            mute.setManaged(false);
        }
    }

    @Override
    public void initFromCommand(CommandMedia cmd) {
        mediagroup.getToggles().get(switch (cmd.getButton()) {
                    case playPause -> 0;
                    case stop -> 1;
                    case prev -> 2;
                    case next -> 3;
                    case mute -> 4;
                }
        ).setSelected(true);
        cmdMediaSpotify.setSelected(cmd.isSpotify());
        super.initFromCommand(cmd);
    }

    @Override
    public Command buildCommand() {
        return new CommandMedia(VolumeButton.valueOf(((RadioButton) mediagroup.getSelectedToggle()).getId()), cmdMediaSpotify.isSelected());
    }

    @Override
    protected Observable[] determineDependencies() {
        return new Observable[] { mediagroup.selectedToggleProperty(), cmdMediaSpotify.selectedProperty() };
    }
}
