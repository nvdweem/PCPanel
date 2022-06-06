package com.getpcpanel.commands.command;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class CommandVolumeFocus extends CommandVolume implements DialAction {
    @Override
    public void execute(boolean initial, int volume) {
        getSndCtrl().setFocusVolume(volume / 100f);
    }
}
