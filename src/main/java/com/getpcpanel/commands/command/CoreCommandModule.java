package com.getpcpanel.commands.command;

import java.util.List;

import com.getpcpanel.commands.CommandModule;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Core feature module: registers its own command types via the
 * {@link com.getpcpanel.commands.CommandModule} SPI. Adding/removing a command touches only this package.
 */
@ApplicationScoped
public class CoreCommandModule implements CommandModule {
    @Override
    public List<Class<? extends Command>> commandTypes() {
        return List.of(
                CommandAnalogBands.class,
                CommandBrightness.class,
                CommandEndProgram.class,
                CommandHttpRequest.class,
                CommandKeystroke.class,
                CommandMedia.class,
                CommandNoOp.class,
                CommandProfile.class,
                CommandRun.class,
                CommandShortcut.class,
                CommandVolumeApplicationDeviceToggle.class,
                CommandVolumeDefaultDevice.class,
                CommandVolumeDefaultDeviceAdvanced.class,
                CommandVolumeDefaultDeviceToggle.class,
                CommandVolumeDefaultDeviceToggleAdvanced.class,
                CommandVolumeDevice.class,
                CommandVolumeDeviceMute.class,
                CommandVolumeFocus.class,
                CommandVolumeFocusMute.class,
                CommandVolumeProcess.class,
                CommandVolumeProcessMute.class);
    }
}
