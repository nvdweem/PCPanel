package com.getpcpanel.ui.command;

import com.getpcpanel.MainFX;
import com.getpcpanel.ui.command.Cmd.CmdEnabled;
import com.getpcpanel.voicemeeter.Voicemeeter;

public class VoiceMeeterEnabled extends CmdEnabled {
    @Override
    public boolean isEnabled() {
        return MainFX.getBean(Voicemeeter.class).login();
    }
}
