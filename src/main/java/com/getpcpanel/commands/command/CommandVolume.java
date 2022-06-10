package com.getpcpanel.commands.command;

import com.getpcpanel.MainFX;
import com.getpcpanel.cpp.ISndCtrl;

import lombok.ToString;

@ToString(callSuper = true)
public abstract class CommandVolume extends Command {
    protected ISndCtrl getSndCtrl() {
        return MainFX.getBean(ISndCtrl.class);
    }
}
