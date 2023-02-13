package com.getpcpanel.ui.command.button;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandVolumeFocusMute;
import com.getpcpanel.cpp.MuteType;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.CommandContext;
import com.getpcpanel.ui.command.CommandController;

import javafx.fxml.FXML;
import javafx.scene.control.RadioButton;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
public class BtnFocusMuteController implements CommandController<CommandVolumeFocusMute> {
    @FXML private RadioButton rdio_focus_mute_mute;
    @FXML private RadioButton rdio_focus_mute_toggle;
    @FXML private RadioButton rdio_focus_mute_unmute;

    @Override
    public void postInit(CommandContext context, Command cmd) {
    }

    @Override
    public void initFromCommand(CommandVolumeFocusMute cmd) {
        switch (cmd.getMuteType()) {
            case unmute -> rdio_focus_mute_unmute.setSelected(true);
            case mute -> rdio_focus_mute_mute.setSelected(true);
            case toggle -> rdio_focus_mute_toggle.setSelected(true);
        }
    }

    @Override
    public Command buildCommand() {
        return new CommandVolumeFocusMute(rdio_focus_mute_unmute.isSelected() ? MuteType.unmute : rdio_focus_mute_mute.isSelected() ? MuteType.mute : MuteType.toggle);
    }
}
