package com.getpcpanel.graalvm;

import org.hid4java.jna.DarwinHidApiLibrary;
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
import com.getpcpanel.commands.command.AnalogBand;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandAnalogBands;
import com.getpcpanel.commands.command.CommandBrightness;
import com.getpcpanel.commands.command.CommandEndProgram;
import com.getpcpanel.commands.command.CommandHttpRequest;
import com.getpcpanel.commands.command.CommandKeystroke;
import com.getpcpanel.commands.command.CommandMedia;
import com.getpcpanel.commands.command.CommandMedia.VolumeButton;
import com.getpcpanel.commands.command.CommandMqttPublish;
import com.getpcpanel.commands.command.CommandNoOp;
import com.getpcpanel.commands.command.CommandObs;
import com.getpcpanel.commands.command.CommandObsAction;
import com.getpcpanel.commands.command.CommandObsAction.ObsActionType;
import com.getpcpanel.commands.command.CommandObsMuteSource;
import com.getpcpanel.commands.command.CommandObsSetScene;
import com.getpcpanel.commands.command.CommandObsSetSourceVolume;
import com.getpcpanel.commands.command.CommandOscSend;
import com.getpcpanel.commands.command.CommandProfile;
import com.getpcpanel.commands.command.CommandRun;
import com.getpcpanel.commands.command.CommandShortcut;
import com.getpcpanel.commands.command.CommandValueOutput;
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
import com.getpcpanel.homeassistant.command.CommandHomeAssistant;
import com.getpcpanel.homeassistant.command.CommandHomeAssistantAction;
import com.getpcpanel.homeassistant.command.CommandHomeAssistantValue;
import com.getpcpanel.homeassistant.dto.HomeAssistantServer;
import com.getpcpanel.homeassistant.dto.HomeAssistantServerStatus;
import com.getpcpanel.device.descriptor.AnalogInputSpec;
import com.getpcpanel.device.descriptor.AnalogKind;
import com.getpcpanel.device.descriptor.AnalogOutputSpec;
import com.getpcpanel.device.descriptor.DeviceDescriptor;
import com.getpcpanel.device.descriptor.DigitalInputSpec;
import com.getpcpanel.device.descriptor.DiscoveryMode;
import com.getpcpanel.device.descriptor.GlobalLightingSpec;
import com.getpcpanel.device.descriptor.LightColorModel;
import com.getpcpanel.device.descriptor.LightGroupKind;
import com.getpcpanel.device.descriptor.LightOutputSpec;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.profile.Save;
import com.getpcpanel.profile.dto.FocusVolumeOverride;
import com.getpcpanel.profile.dto.FocusVolumeTarget;
import com.getpcpanel.profile.dto.KnobSetting;
import com.getpcpanel.profile.dto.LightingConfig;
import com.getpcpanel.profile.dto.LightingConfig.LightingMode;
import com.getpcpanel.profile.dto.MqttSettings;
import com.getpcpanel.profile.dto.MqttSettings.HomeAssistantSettings;
import com.getpcpanel.profile.dto.OSCBinding;
import com.getpcpanel.profile.dto.OSCConnectionInfo;
import com.getpcpanel.profile.dto.OverlayPosition;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig.SINGLE_KNOB_MODE;
import com.getpcpanel.profile.dto.SingleLogoLightingConfig;
import com.getpcpanel.profile.dto.SingleLogoLightingConfig.SINGLE_LOGO_MODE;
import com.getpcpanel.profile.dto.SingleSliderLabelLightingConfig;
import com.getpcpanel.profile.dto.SingleSliderLabelLightingConfig.SINGLE_SLIDER_LABEL_MODE;
import com.getpcpanel.profile.dto.SingleSliderLightingConfig;
import com.getpcpanel.profile.dto.SingleSliderLightingConfig.SINGLE_SLIDER_MODE;
import com.getpcpanel.profile.dto.WaveLinkSettings;
import com.getpcpanel.rest.model.dto.AddDeejDeviceDto;
import com.getpcpanel.rest.model.dto.MidiDeviceDto;
import com.getpcpanel.rest.model.dto.OnboardingDto;
import com.getpcpanel.rest.model.dto.SerialPortDto;
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
import dev.niels.wavelink.impl.rpc.JsonRpcResponse.ErrorDetail;
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
import io.quarkus.runtime.annotations.RegisterForReflection;

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
        // hid4java 0.8 loads hidapi on macOS through this platform-specific Library (Native.load creates
        // a JNA proxy — also registered in proxy-config.json). Without it HID scanning fails in the
        // native image on macOS.
        DarwinHidApiLibrary.class,
        HidDeviceInfoStructure.class,
        HidDeviceStructure.class,
        HidrawHidApiLibrary.class,
        LibusbHidApiLibrary.class,
        WideStringBuffer.class,

        // MQTT Home Assistant discovery payload classes (serialised to JSON by Jackson)
        // Note: these records are package-private so referenced by classNames below

        // Command type hierarchy
        Command.class,
        CommandAnalogBands.class,
        CommandBrightness.class,
        CommandEndProgram.class,
        CommandKeystroke.class,
        CommandMedia.class,
        VolumeButton.class,
        CommandNoOp.class,
        CommandObs.class,
        CommandObsAction.class,
        ObsActionType.class,
        CommandObsMuteSource.class,
        CommandObsSetScene.class,
        CommandObsSetSourceVolume.class,
        CommandProfile.class,
        CommandRun.class,
        CommandShortcut.class,

        // Generic output commands (HTTP / MQTT / OSC) — also extend Command → ID.CLASS polymorphism
        CommandValueOutput.class,
        CommandHttpRequest.class,
        CommandMqttPublish.class,
        CommandOscSend.class,
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

        // Home Assistant command hierarchy (also extends Command → ID.CLASS polymorphism)
        CommandHomeAssistant.class,
        CommandHomeAssistantValue.class,
        CommandHomeAssistantAction.class,

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
        ErrorDetail.class,
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

        // Device capability descriptor (serialised in DeviceDto / DeviceSnapshotDto, sent to the UI)
        DeviceDescriptor.class,
        AnalogInputSpec.class,
        DigitalInputSpec.class,
        LightOutputSpec.class,
        AnalogOutputSpec.class,
        GlobalLightingSpec.class,
        AnalogKind.class,
        LightColorModel.class,
        LightGroupKind.class,
        DiscoveryMode.class,
        // Array forms of the descriptor's List<record> elements: Jackson serialising a List<record>
        // over REST/WS in the native image needs the element's array type reachable too, and these
        // records were not present when reachability-metadata.json was last traced.
        AnalogInputSpec[].class,
        DigitalInputSpec[].class,
        LightOutputSpec[].class,
        AnalogOutputSpec[].class,

        // Serial (Deej) REST DTOs serialised by Jackson
        SerialPortDto.class,
        AddDeejDeviceDto.class,

        // MIDI REST DTO serialised by Jackson
        MidiDeviceDto.class,

        // Onboarding hint REST DTO (GET /api/system/onboarding), serialised by Jackson
        OnboardingDto.class,

        // Home Assistant: server config persisted in the save file + REST DTOs (List<record> needs the
        // element AND its array type reachable for Jackson serialisation in the native image).
        HomeAssistantServer.class,
        HomeAssistantServer[].class,
        HomeAssistantServerStatus.class,
        HomeAssistantServerStatus[].class,

        // Command support types serialised by Jackson
        Commands.class,
        CommandsType.class,
        DeviceSet.class,
        DeviceSet[].class, // CommandVolumeApplicationDeviceToggle.devices is a List<DeviceSet>
        DialCommandParams.class,
        // CommandAnalogBands.bands is a List<AnalogBand>; Jackson needs the record and its array form
        AnalogBand.class,
        AnalogBand[].class,

        // Profile / save model classes (Jackson deserialization of user save file)
        Save.class,
        DeviceSave.class,
        Profile.class,
        LightingConfig.class,
        LightingMode.class,
        SingleKnobLightingConfig.class,
        SINGLE_KNOB_MODE.class,
        SingleSliderLightingConfig.class,
        SINGLE_SLIDER_MODE.class,
        SingleSliderLabelLightingConfig.class,
        SINGLE_SLIDER_LABEL_MODE.class,
        SingleLogoLightingConfig.class,
        SINGLE_LOGO_MODE.class,
        KnobSetting.class,
        MqttSettings.class,
        HomeAssistantSettings.class,
        WaveLinkSettings.class,
        OSCConnectionInfo.class,
        OSCBinding.class,
        OverlayPosition.class,

        // Focus-volume override rules persisted in the save file + sent in SettingsDto. Each is a List on
        // its container (Save.focusVolumeOverrides, FocusVolumeOverride.targets), so Jackson needs the
        // element record AND its array form reachable for native serialisation.
        FocusVolumeOverride.class,
        FocusVolumeOverride[].class,
        FocusVolumeTarget.class,
        FocusVolumeTarget[].class,
}, classNames = {
        // Jackson selects FileSerializer at runtime to serialise a java.io.File field (e.g.
        // ISndCtrl.RunningApplication.file, returned by GET /api/audio/applications). Its no-arg
        // constructor must be reflectively instantiable, or the endpoint 500s in the native image with
        // "FileSerializer has no default constructor" the moment the list is non-empty (works in JVM/dev).
        "com.fasterxml.jackson.databind.ser.std.FileSerializer",

        // Eclipse Paho instantiates its logger reflectively in LoggerFactory.getLogger().
        "org.eclipse.paho.mqttv5.client.logging.JSR47Logger",
        // Paho resolves the transport for a URI scheme (tcp/ssl/ws/wss) through a ServiceLoader of
        // NetworkModuleFactory; the implementations must be reflectively instantiable in native.
        "org.eclipse.paho.mqttv5.client.internal.TCPNetworkModuleFactory",
        "org.eclipse.paho.mqttv5.client.internal.SSLNetworkModuleFactory",
        "org.eclipse.paho.mqttv5.client.websocket.WebSocketNetworkModuleFactory",
        "org.eclipse.paho.mqttv5.client.websocket.WebSocketSecureNetworkModuleFactory",

        // GitHub release version model – deserialised by a plain ObjectMapper in VersionChecker,
        // so Quarkus does not auto-detect it for reflection (records need their canonical creator).
        "com.getpcpanel.util.version.Version",
        "com.getpcpanel.util.version.Version$SemVer",

        // MQTT button-click event payload (package-private record); Jackson reads its accessor
        // reflectively to serialise it on each button press while MQTT is connected.
        "com.getpcpanel.mqtt.MqttDeviceService$MqttEvent",

        // MQTT Home Assistant discovery records (package-private inner classes – referenced by name)
        "com.getpcpanel.mqtt.MqttHomeAssistantHelper$HomeAssistantAvailability",
        "com.getpcpanel.mqtt.MqttHomeAssistantHelper$HomeAssistantButtonConfig",
        "com.getpcpanel.mqtt.MqttHomeAssistantHelper$HomeAssistantButtonEventConfig",
        "com.getpcpanel.mqtt.MqttHomeAssistantHelper$HomeAssistantDevice",
        "com.getpcpanel.mqtt.MqttHomeAssistantHelper$HomeAssistantLightConfig",
        "com.getpcpanel.mqtt.MqttHomeAssistantHelper$HomeAssistantNumberConfig",

        // macOS CoreAudio path (GET /api/audio/*, default-device switching). JNA reflectively
        // instantiates these Structures (no-arg constructor + field access). The CoreFoundation class
        // initializer itself builds a CFTypeID, so the whole jna-platform CoreFoundation inner-class
        // set must be reachable or the audio endpoints 500 in the native image with
        // MissingReflectionRegistrationError / "CFTypeID requires a public no-arg constructor" — even
        // though it works in JVM/dev. (The CoreFoundation JNA *proxy* is registered separately in
        // proxy-config.json.) Registering the full set avoids whack-a-mole across rebuilds.
        "com.sun.jna.platform.mac.CoreFoundation",
        "com.sun.jna.platform.mac.CoreFoundation$CFAllocatorRef",
        "com.sun.jna.platform.mac.CoreFoundation$CFArrayRef",
        "com.sun.jna.platform.mac.CoreFoundation$CFBooleanRef",
        "com.sun.jna.platform.mac.CoreFoundation$CFDataRef",
        "com.sun.jna.platform.mac.CoreFoundation$CFDictionaryRef",
        "com.sun.jna.platform.mac.CoreFoundation$CFDictionaryRef$ByReference",
        "com.sun.jna.platform.mac.CoreFoundation$CFIndex",
        "com.sun.jna.platform.mac.CoreFoundation$CFMutableDictionaryRef",
        "com.sun.jna.platform.mac.CoreFoundation$CFNumberRef",
        "com.sun.jna.platform.mac.CoreFoundation$CFNumberType",
        "com.sun.jna.platform.mac.CoreFoundation$CFStringRef",
        "com.sun.jna.platform.mac.CoreFoundation$CFStringRef$ByReference",
        "com.sun.jna.platform.mac.CoreFoundation$CFTypeID",
        "com.sun.jna.platform.mac.CoreFoundation$CFTypeRef",
        // Project CoreAudio JNA binding: the property-address Structure (instantiated per call) and the
        // change-listener Callback (used for default-device/volume notifications).
        "com.getpcpanel.cpp.osx.CoreAudioLib$AudioObjectPropertyAddress",
        "com.getpcpanel.cpp.osx.CoreAudioLib$AudioObjectPropertyListenerProc",

        // JNA by-reference pointer types that appear in project Library method signatures. JNA
        // reflectively instantiates these via their public no-arg constructor when marshalling the
        // call, so each must be registered or the call throws IllegalArgumentException /
        // MissingReflectionRegistrationError in the native image (works fine in JVM/dev).
        //   - ByteByReference: CoreAudioLib.AudioObjectIsPropertySettable — reached by every macOS
        //     volume/mute write via CoreAudioWrapper.isSettable() (#105).
        //   - ShortByReference: LinuxX11.LibXext.DPMSInfo — the Linux display-power (DPMS) sleep check.
        //   - IntByReference: cross-platform — CoreAudioLib.AudioObjectGetPropertyData(Size) (macOS),
        //     LinuxX11.DPMSInfo/DPMSQueryExtension and LinuxKeyboard.XDisplayKeycodes/XGetKeyboardMapping
        //     (Linux), and the Windows EnumProcesses/GetWindowThreadProcessId lookup. Registered here (not
        //     in the Windows-only-named JnaWin32ReflectionConfig) so a future platform-gate of that class
        //     can't silently drop it on macOS/Linux.
        // PointerByReference is registered via reachability-metadata.json.
        // JnaPointerRegistrationCoverageTest guards this set so a new ByReference type in a Library
        // signature can't ship unregistered.
        "com.sun.jna.ptr.ByteByReference",
        "com.sun.jna.ptr.ShortByReference",
        "com.sun.jna.ptr.IntByReference",
})
public class NativeImageConfig {
    private NativeImageConfig() {
    }
}
