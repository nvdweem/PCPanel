package com.getpcpanel.ui.command;

import com.getpcpanel.MainFX;
import com.getpcpanel.obs.OBS;
import com.getpcpanel.ui.command.Cmd.CmdEnabled;

public class ObsEnabled extends CmdEnabled {
    @Override
    public boolean isEnabled() {
        return MainFX.getBean(OBS.class).isConnected();
    }
}
