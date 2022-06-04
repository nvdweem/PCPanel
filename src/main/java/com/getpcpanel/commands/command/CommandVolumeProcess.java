package com.getpcpanel.commands.command;

import java.util.List;

import com.getpcpanel.cpp.SndCtrl;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class CommandVolumeProcess extends CommandVolume implements DialAction {
    private final List<String> processName;
    private final String device;

    public CommandVolumeProcess(List<String> processName, String device) {
        this.processName = processName;
        this.device = device;
    }

    @Override
    public void execute(int volume) {
        processName.forEach(process -> SndCtrl.setProcessVolume(process, device, volume / 100f));
    }
}
