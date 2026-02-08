package com.getpcpanel.wavelink.ui;

import com.getpcpanel.MainFX;
import com.getpcpanel.ui.command.Cmd.CmdEnabled;
import com.getpcpanel.wavelink.WaveLinkService;

class WaveLinkEnabled extends CmdEnabled {
    @Override
    public boolean isEnabled() {
        return MainFX.getBean(WaveLinkService.class).isEnabled();
    }
}
