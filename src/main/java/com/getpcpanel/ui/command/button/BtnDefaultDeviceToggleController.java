package com.getpcpanel.ui.command.button;

import java.util.Collection;

import javax.annotation.Nullable;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandVolumeDefaultDeviceToggle;
import com.getpcpanel.cpp.AudioDevice;
import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.SoundDeviceExportFactory;
import com.getpcpanel.ui.SoundDeviceImportFactory;
import com.getpcpanel.ui.command.ButtonCommandController;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.CommandContext;

import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
@Cmd(name = "Toggle Device", fxml = "DefaultDeviceToggle", cmds = CommandVolumeDefaultDeviceToggle.class)
public class BtnDefaultDeviceToggleController implements ButtonCommandController<CommandVolumeDefaultDeviceToggle> {
    private final ISndCtrl sndCtrl;
    private Collection<AudioDevice> allSoundDevices;
    @FXML private ListView<AudioDevice> soundDevices2;
    @FXML private ListView<AudioDevice> soundDeviceSource;

    @Override
    public void postInit(CommandContext context) {
        allSoundDevices = sndCtrl.getDevices();
        soundDeviceSource.getItems().addAll(allSoundDevices);
        initDeviceToggleEvents();
        soundDevices2.setCellFactory(new SoundDeviceImportFactory(soundDevices2));
    }

    @Override
    public void initFromCommand(CommandVolumeDefaultDeviceToggle cmd) {
        var devices = StreamEx.of(cmd.getDevices()).map(this::getSoundDeviceById).toList();
        soundDevices2.getItems().addAll(devices);
        soundDeviceSource.getItems().removeAll(devices);
    }

    @Override
    public Command buildCommand() {
        return new CommandVolumeDefaultDeviceToggle(soundDevices2.getItems().stream().map(AudioDevice::id).toList());
    }

    private void initDeviceToggleEvents() {
        var sourceRenderer = new SoundDeviceExportFactory(soundDeviceSource);
        disableDeviceToggleOtherTypes(sourceRenderer);
        soundDeviceSource.setCellFactory(sourceRenderer);
        soundDeviceSource.getItems().addListener((ListChangeListener<AudioDevice>) change -> disableDeviceToggleOtherTypes(sourceRenderer));
    }

    private void disableDeviceToggleOtherTypes(SoundDeviceExportFactory sourceRenderer) {
        if (!soundDevices2.getItems().isEmpty()) {
            var df = soundDevices2.getItems().get(0).dataflow();
            sourceRenderer.setEnabledFlavor(df);
        } else {
            sourceRenderer.setEnabledFlavor(null);
        }
        soundDeviceSource.refresh();
    }

    private @Nullable AudioDevice getSoundDeviceById(String id) {
        return StreamEx.of(allSoundDevices).findFirst(sd -> sd.id().equals(id)).orElse(null);
    }
}
