package com.getpcpanel.commands.command;

import com.getpcpanel.cpp.SndCtrl;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class CommandVolumeFocus extends CommandVolume implements DialAction {
    @Override
    public void execute(int volume) {
        SndCtrl.setFocusVolume(volume / 100f);
    }
}
