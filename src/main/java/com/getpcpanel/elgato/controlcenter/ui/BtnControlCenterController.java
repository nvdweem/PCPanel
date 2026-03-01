package com.getpcpanel.elgato.controlcenter.ui;

import javax.annotation.Nullable;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.cpp.MuteType;
import com.getpcpanel.elgato.controlcenter.ControlCenterService;
import com.getpcpanel.elgato.controlcenter.command.CommandControlCenter;
import com.getpcpanel.elgato.controlcenter.command.CommandControlLightState;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.ButtonCommandController;
import com.getpcpanel.ui.command.Cmd;

import javafx.beans.Observable;
import javafx.fxml.FXML;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Component
@Prototype
@Cmd(name = "ControlCenter", fxml = "ControlCenter", cmds = CommandControlLightState.class, enabled = ControlCenterEnabled.class)
public class BtnControlCenterController extends BaseControlCenterController<CommandControlCenter> implements ButtonCommandController {
    @FXML private RadioButton type_mute;
    @FXML private RadioButton type_mainoutput;
    @FXML private RadioButton type_add_focus_to_channel;
    @FXML private RadioButton type_channel_effect;
    @FXML private ToggleGroup type_grp;

    @FXML private VBox mute_box;
    @FXML private RadioButton mute_toggle;
    @FXML private RadioButton mute_mute;
    @FXML private RadioButton mute_unmute;
    @FXML private ToggleGroup mute_toggle_grp;

    public BtnControlCenterController(ControlCenterService controlCenterService) {
        super(controlCenterService);
    }

    @Override
    public void initFromCommand(CommandControlCenter cmd) {
        switch (cmd) {
            case CommandControlLightState lsCmd -> {
                setMuteToggle(lsCmd.getToggleType());
            }
            default -> log.debug("Unknown command {}", cmd);
        }
        super.initFromCommand(cmd);
    }

    private void setMuteToggle(@Nullable MuteType toggleType) {
        mute_toggle_grp.selectToggle(
                switch (toggleType) {
                    case mute -> mute_mute;
                    case unmute -> mute_unmute;
                    case toggle -> mute_toggle;
                    case null -> mute_toggle;
                }
        );
    }

    @Override
    public Command buildCommand() {
        return new CommandControlLightState(getSelectedDeviceId(), getMuteType());
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
