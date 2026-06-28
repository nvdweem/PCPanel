package com.getpcpanel.mutecolor;

import java.util.Optional;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.integration.volume.command.CommandVolumeDevice;
import com.getpcpanel.cpp.ISndCtrl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Mute state of an audio-device volume control ({@link CommandVolumeDevice}); empty deviceId = the default device. */
@ApplicationScoped
public class DeviceMuteResolver implements MuteStateResolver {
    @Inject
    ISndCtrl sndCtrl;

    @Override
    public Optional<Boolean> resolve(Commands command, String target) {
        if (!FOLLOW.equals(target)) {
            return Optional.empty();
        }
        return command.getCommand(CommandVolumeDevice.class).flatMap(cmd -> {
            var id = sndCtrl.defaultDeviceOnEmpty(cmd.getDeviceId());
            var device = sndCtrl.getDevice(id);
            return device == null ? Optional.empty() : Optional.of(device.muted());
        });
    }
}
