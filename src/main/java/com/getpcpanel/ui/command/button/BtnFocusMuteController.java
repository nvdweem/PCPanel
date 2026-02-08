package com.getpcpanel.ui.command.button;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandVolumeFocusMute;
import com.getpcpanel.cpp.MuteType;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.ButtonCommandController;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.CommandContext;
import com.getpcpanel.ui.command.CommandController;

import javafx.beans.Observable;
import javafx.fxml.FXML;
import javafx.scene.control.RadioButton;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
@Cmd(name = "Focus Mute", fxml = "FocusMute", cmds = CommandVolumeFocusMute.class)
public class BtnFocusMuteController extends CommandController<CommandVolumeFocusMute> implements ButtonCommandController {
    @FXML private RadioButton rdio_focus_mute_mute;
    @FXML private RadioButton rdio_focus_mute_toggle;
    @FXML private RadioButton rdio_focus_mute_unmute;

    @Override
    public void postInit(CommandContext context) {
    }

    @Override
    public void initFromCommand(CommandVolumeFocusMute cmd) {
        switch (cmd.getMuteType()) {
            case unmute -> rdio_focus_mute_unmute.setSelected(true);
            case mute -> rdio_focus_mute_mute.setSelected(true);
            case toggle -> rdio_focus_mute_toggle.setSelected(true);
        }
        super.initFromCommand(cmd);
    }

    @Override
    public Command buildCommand() {
        return new CommandVolumeFocusMute(rdio_focus_mute_unmute.isSelected() ? MuteType.unmute : rdio_focus_mute_mute.isSelected() ? MuteType.mute : MuteType.toggle);
    }

    @Override
    protected Observable[] determineDependencies() {
        return new Observable[] {
                rdio_focus_mute_mute.selectedProperty(),
                rdio_focus_mute_toggle.selectedProperty(),
                rdio_focus_mute_unmute.selectedProperty(),
        };
    }
}
