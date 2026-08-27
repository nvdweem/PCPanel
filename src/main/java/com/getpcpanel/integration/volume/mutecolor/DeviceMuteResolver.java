package com.getpcpanel.integration.volume.mutecolor;

import java.util.Optional;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.integration.volume.command.CommandVolumeDevice;
import com.getpcpanel.integration.volume.command.CommandVolumeDeviceMute;
import com.getpcpanel.integration.volume.platform.ISndCtrl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Mute state of an audio device, followed by either a device-volume dial ({@link CommandVolumeDevice})
 * or a device-mute button ({@link CommandVolumeDeviceMute}); an empty device id = the default device.
 * The dial wins when a control has both, since the device it turns is the control's primary target.
 */
@ApplicationScoped
class DeviceMuteResolver implements MuteStateResolver {
    @Inject
    ISndCtrl sndCtrl;

    @Override
    public Optional<Boolean> resolve(Commands command, String target) {
        if (!FOLLOW.equals(target)) {
            return Optional.empty();
        }
        var dial = command.getCommand(CommandVolumeDevice.class);
        if (dial.isPresent()) {
            return mutedForDevice(dial.get().getDeviceId());
        }
        return command.getCommand(CommandVolumeDeviceMute.class).flatMap(cmd -> mutedForDevice(cmd.getDeviceId()));
    }

    /** Kept separate from the command lookup so a null/blank id still means "the default device"
     *  rather than "no target" (mapping the id through an Optional would swallow it). */
    private Optional<Boolean> mutedForDevice(String deviceId) {
        var device = sndCtrl.getDevice(sndCtrl.defaultDeviceOnEmpty(deviceId));
        return device == null ? Optional.empty() : Optional.of(device.muted());
    }
}
