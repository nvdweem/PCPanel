package com.getpcpanel.integration.volume.command;

import com.getpcpanel.commands.command.ButtonAction;
import java.util.Collection;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.meta.CommandCategory;
import com.getpcpanel.commands.meta.CommandKind;
import com.getpcpanel.commands.meta.CommandMeta;
import com.getpcpanel.integration.volume.EverythingElse;
import com.getpcpanel.integration.volume.MuteFeedback;
import com.getpcpanel.integration.volume.platform.AudioSession;
import com.getpcpanel.integration.volume.platform.MuteType;
import com.getpcpanel.util.CdiHelper;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
@JsonTypeName("volume.process-mute")
@CommandMeta(label = "App mute", category = CommandCategory.audio, kinds = {CommandKind.button}, icon = "volume-x", legacyIds = {"com.getpcpanel.commands.command.CommandVolumeProcessMute"})
public class CommandVolumeProcessMute extends CommandVolume implements ButtonAction {
    private final Set<String> processName;
    /** The output device to mute the apps on; blank (the default) is every device. */
    @Nullable private final String device;
    private final MuteType muteType;

    public CommandVolumeProcessMute(Set<String> processName, MuteType muteType) {
        this(processName, null, muteType);
    }

    @JsonCreator
    public CommandVolumeProcessMute(@JsonProperty("processName") Set<String> processName, @JsonProperty("device") @Nullable String device,
            @JsonProperty("muteType") MuteType muteType) {
        this.processName = processName;
        this.device = device;
        this.muteType = muteType;
    }

    @Override
    public void execute() {
        var snd = getSndCtrl();
        var apps = CdiHelper.getBean(EverythingElse.class).expand(processName);
        var feedback = feedback(() -> MuteFeedback.forSessions(sessionsOnDevice(snd.getAllSessions()), apps, muteType));
        snd.muteProcesses(apps, device, muteType);
        fireFeedback(feedback);
    }

    /** The sessions on the chosen device, for the overlay; all of them for every device, or when none is known there. */
    Collection<AudioSession> sessionsOnDevice(Collection<AudioSession> sessions) {
        if (StringUtils.isBlank(device) || "*".equals(device)) {
            return sessions;
        }
        var onDevice = sessions.stream().filter(s -> device.equals(s.deviceId())).toList();
        return onDevice.isEmpty() ? sessions : onDevice;
    }

    @Override
    public String buildLabel() {
        return muteType + " - " + processName;
    }
}
