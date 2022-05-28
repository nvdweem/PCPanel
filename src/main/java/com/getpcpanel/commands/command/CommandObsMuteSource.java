package com.getpcpanel.commands.command;

import com.getpcpanel.obs.OBS;

import lombok.Getter;

@Getter
public class CommandObsMuteSource extends CommandObs {
    public enum MuteType {
        toggle, mute, unmute
    }

    private final String source;
    private final MuteType type;

    public CommandObsMuteSource(String device, int knob, String source, MuteType type) {
        super(device, knob);
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
