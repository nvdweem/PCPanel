package com.getpcpanel.graalvm;

import io.quarkus.runtime.annotations.RegisterForReflection;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.CommandsType;
import com.getpcpanel.commands.DeviceSet;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandBrightness;
import com.getpcpanel.commands.command.CommandEndProgram;
import com.getpcpanel.commands.command.CommandKeystroke;
import com.getpcpanel.commands.command.CommandMedia;
import com.getpcpanel.commands.command.CommandNoOp;
import com.getpcpanel.commands.command.CommandObs;
import com.getpcpanel.commands.command.CommandObsMuteSource;
import com.getpcpanel.commands.command.CommandObsSetScene;
import com.getpcpanel.commands.command.CommandObsSetSourceVolume;
import com.getpcpanel.commands.command.CommandProfile;
import com.getpcpanel.commands.command.CommandRun;
import com.getpcpanel.commands.command.CommandShortcut;
import com.getpcpanel.commands.command.CommandVoiceMeeter;
import com.getpcpanel.commands.command.CommandVoiceMeeterAdvanced;
import com.getpcpanel.commands.command.CommandVoiceMeeterAdvancedButton;
import com.getpcpanel.commands.command.CommandVoiceMeeterBasic;
import com.getpcpanel.commands.command.CommandVoiceMeeterBasicButton;
import com.getpcpanel.commands.command.CommandVolume;
import com.getpcpanel.commands.command.CommandVolumeApplicationDeviceToggle;
import com.getpcpanel.commands.command.CommandVolumeDefaultDevice;
import com.getpcpanel.commands.command.CommandVolumeDefaultDeviceAdvanced;
import com.getpcpanel.commands.command.CommandVolumeDefaultDeviceToggle;
import com.getpcpanel.commands.command.CommandVolumeDefaultDeviceToggleAdvanced;
import com.getpcpanel.commands.command.CommandVolumeDevice;
import com.getpcpanel.commands.command.CommandVolumeDeviceMute;
import com.getpcpanel.commands.command.CommandVolumeFocus;
import com.getpcpanel.commands.command.CommandVolumeFocusMute;
import com.getpcpanel.commands.command.CommandVolumeProcess;
import com.getpcpanel.commands.command.CommandVolumeProcessMute;
import com.getpcpanel.commands.command.DialAction.DialCommandParams;

/**
 * GraalVM native image reflection hints.
 *
 * <p>Jackson deserialises {@link Command} subtypes via {@code @JsonTypeInfo(use = ID.CLASS)}, so
 * every concrete subtype must be registered for reflection.  All profile/command model classes that
 * are serialised/deserialised by Jackson are collected here.
 *
 * <p>JNA interfaces are covered by {@code reflect-config.json}.
 */
@RegisterForReflection(targets = {
    // Command type hierarchy
    Command.class,
    CommandBrightness.class,
    CommandEndProgram.class,
    CommandKeystroke.class,
    CommandMedia.class,
    CommandMedia.VolumeButton.class,
    CommandNoOp.class,
    CommandObs.class,
    CommandObsMuteSource.class,
    CommandObsSetScene.class,
    CommandObsSetSourceVolume.class,
    CommandProfile.class,
    CommandRun.class,
    CommandShortcut.class,
    CommandVoiceMeeter.class,
    CommandVoiceMeeterAdvanced.class,
    CommandVoiceMeeterAdvancedButton.class,
    CommandVoiceMeeterBasic.class,
    CommandVoiceMeeterBasicButton.class,
    CommandVolume.class,
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
    CommandVolumeProcessMute.class,

    // Command support types serialised by Jackson
    Commands.class,
    CommandsType.class,
    DeviceSet.class,
    DialCommandParams.class,
})
public class NativeImageConfig {
    private NativeImageConfig() {
    }
}
