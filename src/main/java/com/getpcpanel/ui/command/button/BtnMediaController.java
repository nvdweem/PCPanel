package com.getpcpanel.ui.command.button;

import static com.getpcpanel.spring.OsHelper.WINDOWS;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandMedia;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.ButtonCommandController;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.CommandContext;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
@Cmd(name = "Music Control", fxml = "Media", cmds = CommandMedia.class, os = WINDOWS)
public class BtnMediaController implements ButtonCommandController<CommandMedia> {
    @FXML private ToggleGroup mediagroup;
    @FXML private CheckBox cmdMediaSpotify;

    @Override
    public void postInit(CommandContext context) {
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
    }

    @Override
    public Command buildCommand() {
        return new CommandMedia(CommandMedia.VolumeButton.valueOf(((RadioButton) mediagroup.getSelectedToggle()).getId()), cmdMediaSpotify.isSelected());
    }
}
