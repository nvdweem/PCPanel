package com.getpcpanel.commands.command;

import com.getpcpanel.voicemeeter.Voicemeeter;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class CommandVoiceMeeterBasic extends CommandVoiceMeeter implements DialAction {
    private final Voicemeeter.ControlType ct;
    private final int index;
    private final Voicemeeter.DialType dt;

    public CommandVoiceMeeterBasic(Voicemeeter.ControlType ct, int index, Voicemeeter.DialType dt) {
        this.ct = ct;
        this.index = index;
        this.dt = dt;
    }

    @Override
    public void execute(int level) {
        if (Voicemeeter.login()) {
            Voicemeeter.controlLevel(ct, index, dt, level);
        }
    }
}
