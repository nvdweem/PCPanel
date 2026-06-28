package com.getpcpanel.volume.command;

import java.util.List;

import com.getpcpanel.commands.CommandModule;
import com.getpcpanel.commands.command.Command;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Volume feature module: registers its own command types via the {@link com.getpcpanel.commands.CommandModule}
 * SPI. Adding/removing a command touches only this package.
 */
@ApplicationScoped
public class VolumeCommandModule implements CommandModule {
    @Override
    public List<Class<? extends Command>> commandTypes() {
        return List.of(
                CommandVolumeProcess.class,
                CommandVolumeProcessMute.class,
                CommandVolumeFocus.class,
                CommandVolumeFocusMute.class,
                CommandVolumeDevice.class,
                CommandVolumeDeviceMute.class,
                CommandVolumeDefaultDevice.class,
                CommandVolumeDefaultDeviceToggle.class,
                CommandVolumeDefaultDeviceAdvanced.class,
                CommandVolumeDefaultDeviceToggleAdvanced.class,
                CommandVolumeApplicationDeviceToggle.class);
    }
}
