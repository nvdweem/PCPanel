package com.getpcpanel.integration.volume.command;

import com.getpcpanel.commands.command.DialAction;
import java.util.HashSet;
import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.meta.CommandCategory;
import com.getpcpanel.commands.meta.CommandKind;
import com.getpcpanel.commands.meta.CommandMeta;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.integration.volume.EverythingElse;
import com.getpcpanel.commands.command.LevelReadable;
import com.getpcpanel.integration.volume.platform.AudioSession;
import com.getpcpanel.integration.volume.platform.MuteType;
import javax.annotation.Nullable;
import one.util.streamex.StreamEx;
import com.getpcpanel.util.CdiHelper;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
@JsonTypeName("volume.process")
@CommandMeta(label = "App volume", category = CommandCategory.audio, kinds = {CommandKind.dial}, icon = "volume", legacyIds = {"com.getpcpanel.commands.command.CommandVolumeProcess"})
public class CommandVolumeProcess extends CommandVolume implements DialAction, LevelReadable {
    private final List<String> processName;
    /** The output device to control the apps on; blank (the default) is every output device. */
    private final String device;
    private final boolean unMuteOnVolumeChange;
    private final DialCommandParams dialParams;

    @JsonCreator
    public CommandVolumeProcess(
            @JsonProperty("processName") List<String> processName,
            @JsonProperty("device") String device,
            @JsonProperty("isUnMuteOnVolumeChange") boolean unMuteOnVolumeChange,
            @JsonProperty("dialParams") DialCommandParams dialParams) {
        this.processName = processName;
        this.device = device;
        this.unMuteOnVolumeChange = unMuteOnVolumeChange;
        this.dialParams = dialParams;
    }

    @Override
    public void execute(DialActionParameters context) {
        var snd = getSndCtrl();
        var targets = CdiHelper.getBean(EverythingElse.class).expand(processName);
        if (!context.initial() && unMuteOnVolumeChange) {
            snd.muteProcesses(new HashSet<>(targets), MuteType.unmute);
        }
        targets.forEach(process -> snd.setProcessVolume(process, device, context.dial().getValue(this, 0, 1)));
    }

    /** The level of the apps it names; unknown for an app group, which has no single level. */
    @Override
    @Nullable
    public Float readLevel() {
        if (processName == null || processName.stream().anyMatch(EverythingElse::isToken)) {
            return null;
        }
        return levelOf(getSndCtrl().getAllSessions(), processName);
    }

    /**
     * The apps' level, or null when it is unknown: no session, or sessions at different levels (an app with an idle
     * session on another device), where any one of them could be the one that is heard. Each change of this action
     * sets them all alike again.
     */
    @Nullable
    static Float levelOf(Collection<? extends AudioSession> sessions, List<String> names) {
        var levels = StreamEx.of(sessions)
                             .filter(s -> names.stream().anyMatch(s::matches))
                             .map(s -> Math.round(s.volume() * 100) / 100f)
                             .distinct()
                             .limit(2)
                             .toList();
        return levels.size() == 1 ? levels.get(0) : null;
    }

    @Override
    public String buildLabel() {
        return processName + (unMuteOnVolumeChange ? "(unmute)" : "");
    }
}
