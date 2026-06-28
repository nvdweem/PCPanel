package com.getpcpanel.commands.command;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.getpcpanel.discord.command.CommandDiscordJoinVoice;
import com.getpcpanel.discord.command.CommandDiscordLeaveVoice;
import com.getpcpanel.discord.command.CommandDiscordMute;
import com.getpcpanel.discord.command.CommandDiscordScreenShare;
import com.getpcpanel.discord.command.CommandDiscordSelfDeafen;
import com.getpcpanel.discord.command.CommandDiscordSelfInputVolume;
import com.getpcpanel.discord.command.CommandDiscordSelfMute;
import com.getpcpanel.discord.command.CommandDiscordSelfOutputVolume;
import com.getpcpanel.discord.command.CommandDiscordToggleVideo;
import com.getpcpanel.discord.command.CommandDiscordUserMute;
import com.getpcpanel.discord.command.CommandDiscordUserVolume;
import com.getpcpanel.discord.command.CommandDiscordVolume;
import com.getpcpanel.hid.DialValue;
import com.getpcpanel.homeassistant.command.CommandHomeAssistantAction;
import com.getpcpanel.homeassistant.command.CommandHomeAssistantValue;
import com.getpcpanel.voicemeeter.command.CommandVoiceMeeterAdvanced;
import com.getpcpanel.voicemeeter.command.CommandVoiceMeeterAdvancedButton;
import com.getpcpanel.voicemeeter.command.CommandVoiceMeeterBasic;
import com.getpcpanel.voicemeeter.command.CommandVoiceMeeterBasicButton;
import com.getpcpanel.wavelink.command.CommandWaveLinkAddFocusToChannel;
import com.getpcpanel.wavelink.command.CommandWaveLinkChangeLevel;
import com.getpcpanel.wavelink.command.CommandWaveLinkChangeMute;
import com.getpcpanel.wavelink.command.CommandWaveLinkChannelEffect;
import com.getpcpanel.wavelink.command.CommandWaveLinkMainOutput;

import lombok.ToString;
import lombok.extern.log4j.Log4j2;

/**
 * Base type for every assignable action.
 *
 * <p><b>Persisted type id (the {@code _type} discriminator).</b> The polymorphism uses
 * {@link JsonTypeInfo.Id#NAME} with an explicit {@link JsonSubTypes} registry rather than
 * {@code Id.CLASS}. Each subtype's {@code name} is a <em>stable, location-independent string</em>
 * that happens to equal the class's historical fully-qualified name — so saved {@code profiles.json}
 * files, the generated TypeScript {@code _type} union, and the frontend command catalog are all
 * unchanged by this scheme, yet a command class is now free to move to its own feature package
 * (e.g. {@code com.getpcpanel.voicemeeter.command}) without breaking any of them: only the
 * {@code @Type(value = …)} reference moves, the {@code name} stays frozen.
 *
 * <p>This is the seam that lets each integration own its command classes. To add a command: create
 * the class and register one {@code @Type} line here. (A future build-time generator may derive this
 * list and the frontend catalog from per-command {@code @CommandMeta} annotations — see
 * {@code docs/feature-module-structure.md}.)
 */
@Log4j2
@ToString
@SuppressWarnings("InstanceofThis")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "_type")
@JsonSubTypes({
        @Type(value = CommandAnalogBands.class, name = "com.getpcpanel.commands.command.CommandAnalogBands"),
        @Type(value = CommandBrightness.class, name = "com.getpcpanel.commands.command.CommandBrightness"),
        @Type(value = CommandEndProgram.class, name = "com.getpcpanel.commands.command.CommandEndProgram"),
        @Type(value = CommandHttpRequest.class, name = "com.getpcpanel.commands.command.CommandHttpRequest"),
        @Type(value = CommandKeystroke.class, name = "com.getpcpanel.commands.command.CommandKeystroke"),
        @Type(value = CommandMedia.class, name = "com.getpcpanel.commands.command.CommandMedia"),
        @Type(value = CommandMqttPublish.class, name = "com.getpcpanel.commands.command.CommandMqttPublish"),
        @Type(value = CommandNoOp.class, name = "com.getpcpanel.commands.command.CommandNoOp"),
        @Type(value = CommandObsAction.class, name = "com.getpcpanel.commands.command.CommandObsAction"),
        @Type(value = CommandObsMuteSource.class, name = "com.getpcpanel.commands.command.CommandObsMuteSource"),
        @Type(value = CommandObsSetScene.class, name = "com.getpcpanel.commands.command.CommandObsSetScene"),
        @Type(value = CommandObsSetSourceVolume.class, name = "com.getpcpanel.commands.command.CommandObsSetSourceVolume"),
        @Type(value = CommandOscSend.class, name = "com.getpcpanel.commands.command.CommandOscSend"),
        @Type(value = CommandProfile.class, name = "com.getpcpanel.commands.command.CommandProfile"),
        @Type(value = CommandRun.class, name = "com.getpcpanel.commands.command.CommandRun"),
        @Type(value = CommandShortcut.class, name = "com.getpcpanel.commands.command.CommandShortcut"),
        @Type(value = CommandVoiceMeeterAdvanced.class, name = "com.getpcpanel.commands.command.CommandVoiceMeeterAdvanced"),
        @Type(value = CommandVoiceMeeterAdvancedButton.class, name = "com.getpcpanel.commands.command.CommandVoiceMeeterAdvancedButton"),
        @Type(value = CommandVoiceMeeterBasic.class, name = "com.getpcpanel.commands.command.CommandVoiceMeeterBasic"),
        @Type(value = CommandVoiceMeeterBasicButton.class, name = "com.getpcpanel.commands.command.CommandVoiceMeeterBasicButton"),
        @Type(value = CommandVolumeApplicationDeviceToggle.class, name = "com.getpcpanel.commands.command.CommandVolumeApplicationDeviceToggle"),
        @Type(value = CommandVolumeDefaultDevice.class, name = "com.getpcpanel.commands.command.CommandVolumeDefaultDevice"),
        @Type(value = CommandVolumeDefaultDeviceAdvanced.class, name = "com.getpcpanel.commands.command.CommandVolumeDefaultDeviceAdvanced"),
        @Type(value = CommandVolumeDefaultDeviceToggle.class, name = "com.getpcpanel.commands.command.CommandVolumeDefaultDeviceToggle"),
        @Type(value = CommandVolumeDefaultDeviceToggleAdvanced.class, name = "com.getpcpanel.commands.command.CommandVolumeDefaultDeviceToggleAdvanced"),
        @Type(value = CommandVolumeDevice.class, name = "com.getpcpanel.commands.command.CommandVolumeDevice"),
        @Type(value = CommandVolumeDeviceMute.class, name = "com.getpcpanel.commands.command.CommandVolumeDeviceMute"),
        @Type(value = CommandVolumeFocus.class, name = "com.getpcpanel.commands.command.CommandVolumeFocus"),
        @Type(value = CommandVolumeFocusMute.class, name = "com.getpcpanel.commands.command.CommandVolumeFocusMute"),
        @Type(value = CommandVolumeProcess.class, name = "com.getpcpanel.commands.command.CommandVolumeProcess"),
        @Type(value = CommandVolumeProcessMute.class, name = "com.getpcpanel.commands.command.CommandVolumeProcessMute"),
        @Type(value = CommandDiscordJoinVoice.class, name = "com.getpcpanel.discord.command.CommandDiscordJoinVoice"),
        @Type(value = CommandDiscordLeaveVoice.class, name = "com.getpcpanel.discord.command.CommandDiscordLeaveVoice"),
        @Type(value = CommandDiscordMute.class, name = "com.getpcpanel.discord.command.CommandDiscordMute"),
        @Type(value = CommandDiscordScreenShare.class, name = "com.getpcpanel.discord.command.CommandDiscordScreenShare"),
        @Type(value = CommandDiscordSelfDeafen.class, name = "com.getpcpanel.discord.command.CommandDiscordSelfDeafen"),
        @Type(value = CommandDiscordSelfInputVolume.class, name = "com.getpcpanel.discord.command.CommandDiscordSelfInputVolume"),
        @Type(value = CommandDiscordSelfMute.class, name = "com.getpcpanel.discord.command.CommandDiscordSelfMute"),
        @Type(value = CommandDiscordSelfOutputVolume.class, name = "com.getpcpanel.discord.command.CommandDiscordSelfOutputVolume"),
        @Type(value = CommandDiscordToggleVideo.class, name = "com.getpcpanel.discord.command.CommandDiscordToggleVideo"),
        @Type(value = CommandDiscordUserMute.class, name = "com.getpcpanel.discord.command.CommandDiscordUserMute"),
        @Type(value = CommandDiscordUserVolume.class, name = "com.getpcpanel.discord.command.CommandDiscordUserVolume"),
        @Type(value = CommandDiscordVolume.class, name = "com.getpcpanel.discord.command.CommandDiscordVolume"),
        @Type(value = CommandHomeAssistantAction.class, name = "com.getpcpanel.homeassistant.command.CommandHomeAssistantAction"),
        @Type(value = CommandHomeAssistantValue.class, name = "com.getpcpanel.homeassistant.command.CommandHomeAssistantValue"),
        @Type(value = CommandWaveLinkAddFocusToChannel.class, name = "com.getpcpanel.wavelink.command.CommandWaveLinkAddFocusToChannel"),
        @Type(value = CommandWaveLinkChangeLevel.class, name = "com.getpcpanel.wavelink.command.CommandWaveLinkChangeLevel"),
        @Type(value = CommandWaveLinkChangeMute.class, name = "com.getpcpanel.wavelink.command.CommandWaveLinkChangeMute"),
        @Type(value = CommandWaveLinkChannelEffect.class, name = "com.getpcpanel.wavelink.command.CommandWaveLinkChannelEffect"),
        @Type(value = CommandWaveLinkMainOutput.class, name = "com.getpcpanel.wavelink.command.CommandWaveLinkMainOutput"),
})
public abstract class Command {
    public Runnable toRunnable(boolean initial, String deviceId, @Nullable DialValue vol) {
        // A command that is both a dial and a button action (e.g. the generic HTTP/MQTT/OSC outputs)
        // runs as a dial when a dial value is present, so {{value}} maps from the knob position; on a
        // button (vol == null) it falls through to the button path. Single-role commands are unaffected.
        if (vol != null && this instanceof DialAction da) {
            return da.toRunnable(new DialAction.DialActionParameters(deviceId, initial, vol));
        }
        if (this instanceof ButtonAction ba) {
            return ba.toRunnable();
        }
        if (this instanceof DeviceAction da) {
            return da.toRunnable(new DeviceAction.DeviceActionParameters(deviceId));
        }
        log.error("Unable to convert {} to Runnable ({}, {})", this, deviceId, vol);
        return () -> {
        };
    }

    public abstract String buildLabel();
}
