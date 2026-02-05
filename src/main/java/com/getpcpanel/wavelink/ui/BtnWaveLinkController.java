package com.getpcpanel.wavelink.ui;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.cpp.MuteType;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.ButtonCommandController;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.wavelink.WaveLinkService;
import com.getpcpanel.wavelink.command.CommandWaveLink;
import com.getpcpanel.wavelink.command.CommandWaveLinkMute;

import javafx.beans.Observable;
import javafx.fxml.FXML;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Component
@Prototype
@Cmd(name = "WaveLink", fxml = "WaveLink", cmds = CommandWaveLinkMute.class, enabled = WaveLinkEnabled.class)
public class BtnWaveLinkController extends BaseWaveLinkController<CommandWaveLink> implements ButtonCommandController {
    @FXML private RadioButton mute_toggle;
    @FXML private RadioButton mute_mute;
    @FXML private RadioButton mute_unmute;
    @FXML private ToggleGroup mute_toggle_grp;

    public BtnWaveLinkController(WaveLinkService waveLinkService) {
        super(waveLinkService);
    }

    @Override
    public void initFromCommand(CommandWaveLink cmd) {
        if (cmd instanceof CommandWaveLinkMute muteCmd) {
            mute_toggle_grp.selectToggle(
                    switch (muteCmd.getMuteType()) {
                        case mute -> mute_mute;
                        case unmute -> mute_unmute;
                        case toggle -> mute_toggle;
                    }
            );
        }
        super.initFromCommand(cmd);
    }

    @Override
    public Command buildCommand() {
        var base = buildArgBase();
        return new CommandWaveLinkMute(base.target(), base.id1(), base.id2(), getMuteType());
    }

    private MuteType getMuteType() {
        if (mute_mute.isSelected())
            return MuteType.mute;
        if (mute_unmute.isSelected())
            return MuteType.unmute;
        return MuteType.toggle;
    }

    @Override
    protected Observable[] determineDependencies() {
        return StreamEx.of(super.determineDependencies())
                       .append(mute_toggle_grp.selectedToggleProperty())
                       .toArray(Observable[]::new);
    }
}
