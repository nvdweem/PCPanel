package com.getpcpanel.ui.command.button;

import java.util.Collection;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandVolumeDeviceMute;
import com.getpcpanel.cpp.AudioDevice;
import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.cpp.MuteType;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.ButtonCommandController;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.CommandContext;
import com.getpcpanel.ui.command.CommandController;

import javafx.beans.Observable;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.RadioButton;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
@Cmd(name = "Mute Device", fxml = "DeviceMute", cmds = CommandVolumeDeviceMute.class)
public class BtnDeviceMuteController extends CommandController<CommandVolumeDeviceMute> implements ButtonCommandController {
    private final ISndCtrl sndCtrl;
    private Collection<AudioDevice> allSoundDevices;
    @FXML private ChoiceBox<AudioDevice> muteSoundDevice;
    @FXML private RadioButton rdio_muteDevice_Default;
    @FXML private RadioButton rdio_muteDevice_Specific;
    @FXML private RadioButton rdio_muteDevice_mute;
    @FXML private RadioButton rdio_muteDevice_toggle;
    @FXML private RadioButton rdio_muteDevice_unmute;

    @Override
    public void postInit(CommandContext context) {
        allSoundDevices = sndCtrl.getDevices();
        muteSoundDevice.getItems().addAll(allSoundDevices);
    }

    @Override
    public void initFromCommand(CommandVolumeDeviceMute cmd) {
        if (StringUtils.equalsAny(StringUtils.defaultString(cmd.getDeviceId(), ""), "", "default")) {
            rdio_muteDevice_Default.setSelected(true);
        } else {
            rdio_muteDevice_Specific.setSelected(true);
            muteSoundDevice.setValue(getSoundDeviceById(cmd.getDeviceId()));
        }
        switch (cmd.getMuteType()) {
            case unmute -> rdio_muteDevice_unmute.setSelected(true);
            case mute -> rdio_muteDevice_mute.setSelected(true);
            case toggle -> rdio_muteDevice_toggle.setSelected(true);
        }
        onRadioButton(null);
        super.initFromCommand(cmd);
    }

    @Override
    public Command buildCommand() {
        var device = rdio_muteDevice_Default.isSelected() || muteSoundDevice.getValue() == null ? "" : muteSoundDevice.getValue().id();
        return new CommandVolumeDeviceMute(device, rdio_muteDevice_unmute.isSelected() ? MuteType.unmute : rdio_muteDevice_mute.isSelected() ? MuteType.mute : MuteType.toggle);
    }

    @Override
    protected Observable[] determineDependencies() {
        return new Observable[] {
                muteSoundDevice.valueProperty(),
                rdio_muteDevice_Default.selectedProperty(),
                rdio_muteDevice_Specific.selectedProperty(),
                rdio_muteDevice_mute.selectedProperty(),
                rdio_muteDevice_toggle.selectedProperty(),
                rdio_muteDevice_unmute.selectedProperty()
        };
    }

    @Nullable
    private AudioDevice getSoundDeviceById(String id) {
        return StreamEx.of(allSoundDevices).findFirst(sd -> sd.id().equals(id)).orElse(null);
    }

    @FXML
    private void onRadioButton(@Nullable ActionEvent event) {
        muteSoundDevice.setDisable(!rdio_muteDevice_Specific.isSelected());
    }
}
