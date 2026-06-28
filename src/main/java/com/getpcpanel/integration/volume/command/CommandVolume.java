package com.getpcpanel.integration.volume.command;

import com.getpcpanel.commands.command.Command;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.getpcpanel.util.CdiHelper;
import jakarta.inject.Inject;
import com.getpcpanel.integration.volume.platform.ISndCtrl;

import lombok.ToString;

@ToString(callSuper = true)
public abstract class CommandVolume extends Command {
    @JsonIgnore
    protected ISndCtrl getSndCtrl() {
        return CdiHelper.getBean(ISndCtrl.class);
    }
}
