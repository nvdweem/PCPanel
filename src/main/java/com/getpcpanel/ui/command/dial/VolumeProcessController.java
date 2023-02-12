package com.getpcpanel.ui.command.dial;

import java.util.Collection;
import java.util.Optional;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandVolumeProcess;
import com.getpcpanel.cpp.AudioDevice;
import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.PickProcessesController;
import com.getpcpanel.ui.command.CommandController;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.RadioButton;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
public class VolumeProcessController implements CommandController<CommandVolumeProcess> {
    private final ISndCtrl sndCtrl;
    private Collection<AudioDevice> allSoundDevices;

    @FXML private PickProcessesController appVolumeController;
    @FXML private CheckBox cb_app_unmute;
    @FXML private RadioButton rdio_app_output_default;
    @FXML private RadioButton rdio_app_output_specific;
    @FXML private RadioButton rdio_app_output_all;
    @FXML private ChoiceBox<AudioDevice> app_vol_output_device;

    @Override
    public void postInit(Stage stage, Command cmd) {
        appVolumeController.setPickType(PickProcessesController.PickType.soundSource);

        allSoundDevices = sndCtrl.getDevices();
        var outputDevices = allSoundDevices.stream().filter(AudioDevice::isOutput).toList();
        app_vol_output_device.getItems().addAll(outputDevices);
    }

    @Override
    public void initFromCommand(CommandVolumeProcess cmd) {
        appVolumeController.setSelection(cmd.getProcessName());
        cb_app_unmute.setSelected(cmd.isUnMuteOnVolumeChange());

        if (StringUtils.equals(cmd.getDevice(), "*")) {
            rdio_app_output_all.setSelected(true);
        } else if (StringUtils.isNotBlank(cmd.getDevice())) {
            rdio_app_output_specific.setSelected(true);
            app_vol_output_device.setValue(getSoundDeviceById(cmd.getDevice()));
        } else {
            rdio_app_output_default.setSelected(true);
        }
    }

    @Override
    public Command buildCommand() {
        var device =
                rdio_app_output_all.isSelected() ? "*" :
                        rdio_app_output_specific.isSelected() ? Optional.ofNullable(app_vol_output_device.getSelectionModel().getSelectedItem()).map(AudioDevice::id).orElse("") :
                                "";
        return new CommandVolumeProcess(appVolumeController.getSelection(), device, cb_app_unmute.isSelected());
    }

    private @Nullable AudioDevice getSoundDeviceById(String id) {
        return StreamEx.of(allSoundDevices).findFirst(sd -> sd.id().equals(id)).orElse(null);
    }

    @FXML
    private void onRadioButton(@Nullable ActionEvent event) {
        app_vol_output_device.setDisable(!rdio_app_output_specific.isSelected());
    }
}
