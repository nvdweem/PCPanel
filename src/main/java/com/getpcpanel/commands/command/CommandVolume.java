package com.getpcpanel.commands.command;

import com.getpcpanel.util.CdiHelper;
import jakarta.inject.Inject;
import com.getpcpanel.cpp.ISndCtrl;

import lombok.ToString;

@ToString(callSuper = true)
public abstract class CommandVolume extends Command {
    protected ISndCtrl getSndCtrl() {
        return CdiHelper.getBean(ISndCtrl.class);
    }
}
