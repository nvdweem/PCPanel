package com.getpcpanel.commands.command;

import com.getpcpanel.MainFX;
import com.getpcpanel.cpp.DataFlow;
import com.getpcpanel.cpp.Role;
import com.getpcpanel.cpp.windows.SndCtrlWindows;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
@AllArgsConstructor
@ToString(callSuper = true)
public class CommandVolumeDefaultDeviceAdvanced extends CommandVolume implements ButtonAction {
    private final String mediaPb;
    private final String mediaRec;
    private final String communicationPb;
    private final String communicationRec;

    @Override
    public void execute() {
        var windowsSndCtrl = MainFX.getBean(SndCtrlWindows.class);
        windowsSndCtrl.setDefaultDevice(mediaPb, DataFlow.dfRender, Role.roleMultimedia);
        windowsSndCtrl.setDefaultDevice(mediaRec, DataFlow.dfCapture, Role.roleMultimedia);
        windowsSndCtrl.setDefaultDevice(communicationPb, DataFlow.dfRender, Role.roleCommunications);
        windowsSndCtrl.setDefaultDevice(communicationRec, DataFlow.dfCapture, Role.roleCommunications);
    }
}
