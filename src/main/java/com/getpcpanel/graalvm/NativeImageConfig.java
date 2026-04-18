package com.getpcpanel.graalvm;

import io.quarkus.runtime.annotations.RegisterForReflection;

import org.hid4java.jna.HidApi;
import org.hid4java.jna.HidApiLibrary;
import org.hid4java.jna.HidDeviceInfoStructure;
import org.hid4java.jna.HidDeviceStructure;
import org.hid4java.jna.HidrawHidApiLibrary;
import org.hid4java.jna.LibusbHidApiLibrary;
import org.hid4java.jna.WideStringBuffer;

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
import com.getpcpanel.wavelink.command.CommandWaveLink;
import com.getpcpanel.wavelink.command.CommandWaveLinkAddFocusToChannel;
import com.getpcpanel.wavelink.command.CommandWaveLinkChange;
import com.getpcpanel.wavelink.command.CommandWaveLinkChangeLevel;
import com.getpcpanel.wavelink.command.CommandWaveLinkChangeMute;
import com.getpcpanel.wavelink.command.CommandWaveLinkChannelEffect;
import com.getpcpanel.wavelink.command.CommandWaveLinkMainOutput;
import com.getpcpanel.wavelink.command.WaveLinkCommandTarget;
import dev.niels.wavelink.impl.model.WaveLinkApp;
import dev.niels.wavelink.impl.model.WaveLinkChannel;
import dev.niels.wavelink.impl.model.WaveLinkControlAction;
import dev.niels.wavelink.impl.model.WaveLinkEffect;
import dev.niels.wavelink.impl.model.WaveLinkGain;
import dev.niels.wavelink.impl.model.WaveLinkImage;
import dev.niels.wavelink.impl.model.WaveLinkInput;
import dev.niels.wavelink.impl.model.WaveLinkInputDevice;
import dev.niels.wavelink.impl.model.WaveLinkMainOutput;
import dev.niels.wavelink.impl.model.WaveLinkMix;
import dev.niels.wavelink.impl.model.WaveLinkOutput;
import dev.niels.wavelink.impl.model.WaveLinkOutputDevice;
import dev.niels.wavelink.impl.rpc.JsonRpcMessage;
import dev.niels.wavelink.impl.rpc.JsonRpcResponse;
import dev.niels.wavelink.impl.rpc.WaveLinkAddToChannelCommand;
import dev.niels.wavelink.impl.rpc.WaveLinkChannelChangedCommand;
import dev.niels.wavelink.impl.rpc.WaveLinkChannelsChangedCommand;
import dev.niels.wavelink.impl.rpc.WaveLinkFocusedAppChangedCommand;
import dev.niels.wavelink.impl.rpc.WaveLinkGetApplicationInfo;
import dev.niels.wavelink.impl.rpc.WaveLinkGetChannels;
import dev.niels.wavelink.impl.rpc.WaveLinkGetInputDevices;
import dev.niels.wavelink.impl.rpc.WaveLinkGetMixes;
import dev.niels.wavelink.impl.rpc.WaveLinkGetOutputDevices;
import dev.niels.wavelink.impl.rpc.WaveLinkJsonRpcCommand;
import dev.niels.wavelink.impl.rpc.WaveLinkMixChangedCommand;
import dev.niels.wavelink.impl.rpc.WaveLinkOutputDeviceChangedCommand;
import dev.niels.wavelink.impl.rpc.WaveLinkSetChannelCommand;
import dev.niels.wavelink.impl.rpc.WaveLinkSetMixCommand;
import dev.niels.wavelink.impl.rpc.WaveLinkSetOutputDeviceCommand;
import dev.niels.wavelink.impl.rpc.WaveLinkSetSubscription;
import dev.niels.wavelink.impl.rpc.WaveLinkUnknownCommand;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.KnobSetting;
import com.getpcpanel.profile.LightingConfig;
import com.getpcpanel.profile.MqttSettings;
import com.getpcpanel.profile.OSCBinding;
import com.getpcpanel.profile.OSCConnectionInfo;
import com.getpcpanel.profile.OverlayPosition;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.profile.Save;
import com.getpcpanel.profile.SingleKnobLightingConfig;
import com.getpcpanel.profile.SingleLogoLightingConfig;
import com.getpcpanel.profile.SingleSliderLabelLightingConfig;
import com.getpcpanel.profile.SingleSliderLightingConfig;
import com.getpcpanel.profile.WaveLinkSettings;
import com.getpcpanel.rest.EventBroadcaster;

/**
 * GraalVM native image reflection hints.
 *
 * <p>Jackson deserialises {@link Command} subtypes via {@code @JsonTypeInfo(use = ID.CLASS)}, so
 * every concrete subtype must be registered for reflection.  All profile/command model classes that
 * are serialised/deserialised by Jackson are collected here.
 *
 * <p>hid4java JNA structures and library interfaces are also registered here for JNA reflective
 * instantiation and field access.
 */
@RegisterForReflection(targets = {
    // hid4java JNA library + structure classes (JNA needs reflection to instantiate structures)
    HidApi.class,
    HidApiLibrary.class,
    HidDeviceInfoStructure.class,
    HidDeviceStructure.class,
    HidrawHidApiLibrary.class,
    LibusbHidApiLibrary.class,
    WideStringBuffer.class,

    // MQTT Home Assistant discovery payload classes (serialised to JSON by Jackson)
    // Note: these records are package-private so referenced by classNames below

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

    // WaveLink command hierarchy (also extends Command → ID.CLASS polymorphism)
    CommandWaveLink.class,
    CommandWaveLinkAddFocusToChannel.class,
    CommandWaveLinkChange.class,
    CommandWaveLinkChangeLevel.class,
    CommandWaveLinkChangeMute.class,
    CommandWaveLinkChannelEffect.class,
    CommandWaveLinkMainOutput.class,
    WaveLinkCommandTarget.class,

    // WaveLink RPC protocol classes (Jackson @JsonSubTypes / @JsonTypeInfo)
    JsonRpcMessage.class,
    JsonRpcResponse.class,
    JsonRpcResponse.ErrorDetail.class,
    WaveLinkJsonRpcCommand.class,
    WaveLinkChannelChangedCommand.class,
    WaveLinkChannelsChangedCommand.class,
    WaveLinkFocusedAppChangedCommand.class,
    WaveLinkMixChangedCommand.class,
    WaveLinkOutputDeviceChangedCommand.class,
    WaveLinkGetApplicationInfo.class,
    WaveLinkGetChannels.class,
    WaveLinkGetInputDevices.class,
    WaveLinkGetMixes.class,
    WaveLinkGetOutputDevices.class,
    WaveLinkSetChannelCommand.class,
    WaveLinkSetMixCommand.class,
    WaveLinkSetOutputDeviceCommand.class,
    WaveLinkSetSubscription.class,
    WaveLinkAddToChannelCommand.class,
    WaveLinkUnknownCommand.class,

    // WaveLink model classes (deserialised from WaveLink JSON API)
    WaveLinkApp.class,
    WaveLinkChannel.class,
    WaveLinkControlAction.class,
    WaveLinkEffect.class,
    WaveLinkGain.class,
    WaveLinkImage.class,
    WaveLinkInput.class,
    WaveLinkInputDevice.class,
    WaveLinkMainOutput.class,
    WaveLinkMix.class,
    WaveLinkOutput.class,
    WaveLinkOutputDevice.class,

    // Command support types serialised by Jackson
    Commands.class,
    CommandsType.class,
    DeviceSet.class,
    DialCommandParams.class,

    // Profile / save model classes (Jackson deserialization of user save file)
    Save.class,
    DeviceSave.class,
    Profile.class,
    LightingConfig.class,
    LightingConfig.LightingMode.class,
    SingleKnobLightingConfig.class,
    SingleKnobLightingConfig.SINGLE_KNOB_MODE.class,
    SingleSliderLightingConfig.class,
    SingleSliderLightingConfig.SINGLE_SLIDER_MODE.class,
    SingleSliderLabelLightingConfig.class,
    SingleSliderLabelLightingConfig.SINGLE_SLIDER_LABEL_MODE.class,
    SingleLogoLightingConfig.class,
    SingleLogoLightingConfig.SINGLE_LOGO_MODE.class,
    KnobSetting.class,
    MqttSettings.class,
    MqttSettings.HomeAssistantSettings.class,
    WaveLinkSettings.class,
    OSCConnectionInfo.class,
    OSCBinding.class,
    OverlayPosition.class,

    // WebSocket event DTOs (serialised to JSON by Jackson for broadcasting to WebSocket clients)
    EventBroadcaster.WsEvent.class,
    EventBroadcaster.WsKnobEvent.class,
    EventBroadcaster.WsButtonEvent.class,
}, classNames = {
    // MQTT Home Assistant discovery records (package-private inner classes – referenced by name)
    "com.getpcpanel.mqtt.MqttHomeAssistantHelper$HomeAssistantAvailability",
    "com.getpcpanel.mqtt.MqttHomeAssistantHelper$HomeAssistantButtonConfig",
    "com.getpcpanel.mqtt.MqttHomeAssistantHelper$HomeAssistantButtonEventConfig",
    "com.getpcpanel.mqtt.MqttHomeAssistantHelper$HomeAssistantDevice",
    "com.getpcpanel.mqtt.MqttHomeAssistantHelper$HomeAssistantLightConfig",
    "com.getpcpanel.mqtt.MqttHomeAssistantHelper$HomeAssistantNumberConfig",
})
public class NativeImageConfig {
    private NativeImageConfig() {
    }
}
