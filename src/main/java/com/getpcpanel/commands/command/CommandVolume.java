package com.getpcpanel.commands.command;

import com.getpcpanel.MainFX;
import com.getpcpanel.cpp.SndCtrl;

import lombok.ToString;

@ToString(callSuper = true)
public abstract class CommandVolume extends Command {
    protected SndCtrl getSndCtrl() {
        return MainFX.getBean(SndCtrl.class);
    }
}
