package com.getpcpanel.commands.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.obs.OBS;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class CommandObsMuteSource extends CommandObs implements ButtonAction {
    public enum MuteType {
        toggle, mute, unmute
    }

    private final String source;
    private final MuteType type;

    @JsonCreator
    public CommandObsMuteSource(@JsonProperty("source") String source, @JsonProperty("type") MuteType type) {
        this.source = source;
        this.type = type;
    }

    @Override
    public void execute() {
        switch (type) {
            case toggle -> OBS.toggleSourceMute(source);
            case mute -> OBS.setSourceMute(source, true);
            case unmute -> OBS.setSourceMute(source, false);
        }
    }
}
