package com.getpcpanel.commands.command;

import com.getpcpanel.voicemeeter.Voicemeeter;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class CommandVoiceMeeterBasicButton extends CommandVoiceMeeter implements ButtonAction {
    private final Voicemeeter.ControlType ct;
    private final int index;
    private final Voicemeeter.ButtonType bt;

    public CommandVoiceMeeterBasicButton(Voicemeeter.ControlType ct, int index, Voicemeeter.ButtonType bt) {
        this.ct = ct;
        this.index = index;
        this.bt = bt;
    }

    @Override
    public void execute() {
        if (Voicemeeter.login()) {
            Voicemeeter.controlButton(ct, index, bt);
        }
    }
}
