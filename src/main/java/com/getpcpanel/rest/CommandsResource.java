package com.getpcpanel.rest;

import java.util.Collection;
import java.util.List;

import com.getpcpanel.commands.command.CommandBrightness;
import com.getpcpanel.commands.command.CommandEndProgram;
import com.getpcpanel.commands.command.CommandKeystroke;
import com.getpcpanel.commands.command.CommandMedia;
import com.getpcpanel.commands.command.CommandObsMuteSource;
import com.getpcpanel.commands.command.CommandObsSetScene;
import com.getpcpanel.commands.command.CommandObsSetSourceVolume;
import com.getpcpanel.commands.command.CommandRun;
import com.getpcpanel.commands.command.CommandShortcut;
import com.getpcpanel.commands.command.CommandVoiceMeeterAdvanced;
import com.getpcpanel.commands.command.CommandVoiceMeeterAdvancedButton;
import com.getpcpanel.commands.command.CommandVoiceMeeterBasic;
import com.getpcpanel.commands.command.CommandVoiceMeeterBasicButton;
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
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.rest.EventBroadcaster.AssignmentChangedEvent.Kinds;
import com.getpcpanel.rest.model.dto.CommandType;
import com.getpcpanel.rest.model.dto.CommandType.CommandCategory;
import com.getpcpanel.wavelink.command.CommandWaveLinkAddFocusToChannel;
import com.getpcpanel.wavelink.command.CommandWaveLinkChangeLevel;
import com.getpcpanel.wavelink.command.CommandWaveLinkChangeMute;
import com.getpcpanel.wavelink.command.CommandWaveLinkChannelEffect;
import com.getpcpanel.wavelink.command.CommandWaveLinkMainOutput;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import one.util.streamex.StreamEx;

@Path("/api/commands")
@ApplicationScoped
public class CommandsResource {
    @Inject SaveService saveService;

    private static final List<CommandType> commandTypes = List.of(
            new CommandType("Brightness", CommandBrightness.class.getName(), CommandCategory.standard, Kinds.dial),
            new CommandType("Process volume", CommandVolumeProcess.class.getName(), CommandCategory.standard, Kinds.dial),
            new CommandType("Focus volume", CommandVolumeFocus.class.getName(), CommandCategory.standard, Kinds.dial),
            new CommandType("Device volume", CommandVolumeDevice.class.getName(), CommandCategory.standard, Kinds.dial),
            new CommandType("Obs Source Volume", CommandObsSetSourceVolume.class.getName(), CommandCategory.obs, Kinds.dial),
            new CommandType("VoiceMeeter Advanced", CommandVoiceMeeterAdvanced.class.getName(), CommandCategory.voicemeeter, Kinds.dial),
            new CommandType("VoiceMeeter Basic", CommandVoiceMeeterBasic.class.getName(), CommandCategory.voicemeeter, Kinds.dial),
            new CommandType("WaveLink Change Level", CommandWaveLinkChangeLevel.class.getName(), CommandCategory.wavelink, Kinds.dial),

            new CommandType("End Program", CommandEndProgram.class.getName(), CommandCategory.standard, Kinds.button),
            new CommandType("Keystroke", CommandKeystroke.class.getName(), CommandCategory.standard, Kinds.button),
            new CommandType("Run", CommandRun.class.getName(), CommandCategory.standard, Kinds.button),
            new CommandType("Shortcut", CommandShortcut.class.getName(), CommandCategory.standard, Kinds.button),
            new CommandType("Media", CommandMedia.class.getName(), CommandCategory.standard, Kinds.button),
            new CommandType("Toggle application device", CommandVolumeApplicationDeviceToggle.class.getName(), CommandCategory.standard, Kinds.button),
            new CommandType("Default Device", CommandVolumeDefaultDevice.class.getName(), CommandCategory.standard, Kinds.button),
            new CommandType("Default Device Advanced", CommandVolumeDefaultDeviceAdvanced.class.getName(), CommandCategory.standard, Kinds.button),
            new CommandType("Default Device Toggle", CommandVolumeDefaultDeviceToggle.class.getName(), CommandCategory.standard, Kinds.button),
            new CommandType("Default Device Toggle Advanced", CommandVolumeDefaultDeviceToggleAdvanced.class.getName(), CommandCategory.standard, Kinds.button),
            new CommandType("Device Mute", CommandVolumeDeviceMute.class.getName(), CommandCategory.standard, Kinds.button),
            new CommandType("Focus Mute", CommandVolumeFocusMute.class.getName(), CommandCategory.standard, Kinds.button),
            new CommandType("Process Mute", CommandVolumeProcessMute.class.getName(), CommandCategory.standard, Kinds.button),
            new CommandType("Obs Mute Source", CommandObsMuteSource.class.getName(), CommandCategory.obs, Kinds.button),
            new CommandType("Obs Set Scene", CommandObsSetScene.class.getName(), CommandCategory.obs, Kinds.button),
            new CommandType("VoiceMeeter Advanced", CommandVoiceMeeterAdvancedButton.class.getName(), CommandCategory.voicemeeter, Kinds.button),
            new CommandType("VoiceMeeter Basic", CommandVoiceMeeterBasicButton.class.getName(), CommandCategory.voicemeeter, Kinds.button),
            new CommandType("WaveLink Add Focus To Channel", CommandWaveLinkAddFocusToChannel.class.getName(), CommandCategory.wavelink, Kinds.button),
            new CommandType("WaveLink Change Mute", CommandWaveLinkChangeMute.class.getName(), CommandCategory.wavelink, Kinds.button),
            new CommandType("WaveLink Channel Effect", CommandWaveLinkChannelEffect.class.getName(), CommandCategory.wavelink, Kinds.button),
            new CommandType("WaveLink Main Output", CommandWaveLinkMainOutput.class.getName(), CommandCategory.wavelink, Kinds.button)
    );

    @GET
    @Path("/available")
    public Collection<CommandType> listAvailableCommands() {
        return StreamEx.of(commandTypes).filter(this::enabled).toList();
    }

    private boolean enabled(CommandType commandType) {
        return switch (commandType.category()) {
            case standard -> true;
            case obs -> saveService.get().isObsEnabled();
            case voicemeeter -> saveService.get().isVoicemeeterEnabled();
            case wavelink -> saveService.get().getWaveLink().enabled();
        };
    }
}
