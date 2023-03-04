package com.getpcpanel.ui.command.button;

import static com.getpcpanel.spring.OsHelper.WINDOWS;

import java.util.HashSet;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandVolumeProcessMute;
import com.getpcpanel.cpp.MuteType;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.PickProcessesController;
import com.getpcpanel.ui.command.ButtonCommandController;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.CommandContext;

import javafx.fxml.FXML;
import javafx.scene.control.RadioButton;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
@Cmd(name = "Mute App", fxml = "VolumeProcessMute", cmds = CommandVolumeProcessMute.class, os = WINDOWS)
public class BtnVolumeProcessMuteController implements ButtonCommandController<CommandVolumeProcessMute> {
    @FXML private PickProcessesController appMuteController;
    @FXML private RadioButton rdio_mute_mute;
    @FXML private RadioButton rdio_mute_toggle;
    @FXML private RadioButton rdio_mute_unmute;

    @Override
    public void postInit(CommandContext context) {
        appMuteController.setPickType(PickProcessesController.PickType.soundSource);
    }

    @Override
    public void initFromCommand(CommandVolumeProcessMute cmd) {
        appMuteController.setSelection(cmd.getProcessName());
        switch (cmd.getMuteType()) {
            case unmute -> rdio_mute_unmute.setSelected(true);
            case mute -> rdio_mute_mute.setSelected(true);
            case toggle -> rdio_mute_toggle.setSelected(true);
        }
    }

    @Override
    public Command buildCommand() {
        return new CommandVolumeProcessMute(new HashSet<>(appMuteController.getSelection()),
                rdio_mute_unmute.isSelected() ? MuteType.unmute : rdio_mute_mute.isSelected() ? MuteType.mute : MuteType.toggle);
    }
}
