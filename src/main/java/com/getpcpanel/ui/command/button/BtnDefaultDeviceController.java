package com.getpcpanel.ui.command.button;

import static com.getpcpanel.commands.command.CommandNoOp.NOOP;

import java.util.Collection;

import javax.annotation.Nullable;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandVolumeDefaultDevice;
import com.getpcpanel.cpp.AudioDevice;
import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.ButtonCommandController;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.CommandContext;

import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
@Cmd(name = "Sound Device", fxml = "DefaultDevice", cmds = CommandVolumeDefaultDevice.class)
public class BtnDefaultDeviceController implements ButtonCommandController<CommandVolumeDefaultDevice> {
    private final ISndCtrl sndCtrl;
    private Collection<AudioDevice> allSoundDevices;

    @FXML private ChoiceBox<AudioDevice> sounddevices;

    @Override
    public void postInit(CommandContext context) {
        allSoundDevices = sndCtrl.getDevices();
        sounddevices.getItems().addAll(allSoundDevices);
    }

    @Override
    public void initFromCommand(CommandVolumeDefaultDevice cmd) {
        sounddevices.setValue(getSoundDeviceById(cmd.getDeviceId()));
    }

    @Override
    public Command buildCommand() {
        return sounddevices.getValue() == null ? NOOP : new CommandVolumeDefaultDevice(sounddevices.getValue().id());
    }

    private @Nullable AudioDevice getSoundDeviceById(String id) {
        return StreamEx.of(allSoundDevices).findFirst(sd -> sd.id().equals(id)).orElse(null);
    }
}
