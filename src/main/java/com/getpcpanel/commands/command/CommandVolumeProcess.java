package com.getpcpanel.commands.command;

import java.util.HashSet;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.cpp.MuteType;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class CommandVolumeProcess extends CommandVolume implements DialAction {
    private final List<String> processName;
    private final String device;
    private final boolean unMuteOnVolumeChange;

    @JsonCreator
    public CommandVolumeProcess(@JsonProperty("processName") List<String> processName, @JsonProperty("device") String device, @JsonProperty("isUnMuteOnVolumeChange") boolean unMuteOnVolumeChange) {
        this.processName = processName;
        this.device = device;
        this.unMuteOnVolumeChange = unMuteOnVolumeChange;
    }

    @Override
    public void execute(DialActionParameters context) {
        var snd = getSndCtrl();
        if (!context.initial() && unMuteOnVolumeChange) {
            snd.muteProcesses(new HashSet<>(processName), MuteType.unmute);
        }
        processName.forEach(process -> snd.setProcessVolume(process, device, context.dial() / 100f));
    }
}
