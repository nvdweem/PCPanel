package com.getpcpanel.elgato.controlcenter.ui;

import com.getpcpanel.MainFX;
import com.getpcpanel.elgato.controlcenter.ControlCenterService;
import com.getpcpanel.ui.command.Cmd.CmdEnabled;

class ControlCenterEnabled extends CmdEnabled {
    @Override
    public boolean isEnabled() {
        return MainFX.getBean(ControlCenterService.class).isEnabled();
    }
}
