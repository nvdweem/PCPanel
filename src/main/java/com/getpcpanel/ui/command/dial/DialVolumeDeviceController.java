package com.getpcpanel.ui.command.dial;

import java.util.Collection;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandVolumeDevice;
import com.getpcpanel.cpp.AudioDevice;
import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.CommandContext;
import com.getpcpanel.ui.command.DialCommandController;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.RadioButton;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
@Cmd(name = "Device Volume", fxml = "VolumeDevice", cmds = CommandVolumeDevice.class)
public class DialVolumeDeviceController implements DialCommandController<CommandVolumeDevice> {
    private final ISndCtrl sndCtrl;
    private Collection<AudioDevice> allSoundDevices;
    @FXML private CheckBox cb_device_unmute;
    @FXML private ChoiceBox<AudioDevice> volumedevice;
    @FXML private RadioButton rdio_device_default;
    @FXML private RadioButton rdio_device_specific;

    @Override
    public void postInit(CommandContext context) {
        allSoundDevices = sndCtrl.getDevices();
        volumedevice.getItems().addAll(allSoundDevices);
    }

    @Override
    public void initFromCommand(CommandVolumeDevice cmd) {
        if (StringUtils.isNotBlank(cmd.getDeviceId())) {
            rdio_device_specific.setSelected(true);
            volumedevice.setValue(getSoundDeviceById(cmd.getDeviceId()));
        } else {
            rdio_device_default.setSelected(true);
        }
        cb_device_unmute.setSelected(cmd.isUnMuteOnVolumeChange());
        onRadioButton(null);
    }

    @Override
    public Command buildCommand(boolean invert) {
        return new CommandVolumeDevice(
                rdio_device_specific.isSelected() && volumedevice.getSelectionModel().getSelectedItem() != null ? volumedevice.getSelectionModel().getSelectedItem().id() : "",
                cb_device_unmute.isSelected(), invert);
    }

    @FXML
    private void onRadioButton(@Nullable ActionEvent event) {
        volumedevice.setDisable(!rdio_device_specific.isSelected());
    }

    private @Nullable AudioDevice getSoundDeviceById(String id) {
        return StreamEx.of(allSoundDevices).findFirst(sd -> sd.id().equals(id)).orElse(null);
    }
}
