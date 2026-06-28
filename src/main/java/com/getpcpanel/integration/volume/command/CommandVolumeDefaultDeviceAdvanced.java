package com.getpcpanel.integration.volume.command;

import com.getpcpanel.commands.command.ButtonAction;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.meta.CommandCategory;
import com.getpcpanel.commands.meta.CommandKind;
import com.getpcpanel.commands.meta.CommandMeta;
import javax.annotation.Nullable;

import com.getpcpanel.util.CdiHelper;
import com.getpcpanel.integration.volume.platform.DataFlow;
import com.getpcpanel.integration.volume.platform.Role;
import com.getpcpanel.integration.volume.platform.windows.SndCtrlWindows;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.jackson.Jacksonized;
import lombok.extern.log4j.Log4j2;

@Getter
@Log4j2
@Builder
@Jacksonized
@AllArgsConstructor
@ToString(callSuper = true)
@JsonTypeName("volume.default-device-advanced")
@CommandMeta(label = "Advanced default device", category = CommandCategory.audio, kinds = {CommandKind.button}, icon = "monitor", legacyIds = {"com.getpcpanel.commands.command.CommandVolumeDefaultDeviceAdvanced"})
public class CommandVolumeDefaultDeviceAdvanced extends CommandVolume implements ButtonAction {
    private final String name;
    private final String mediaPb;
    private final String mediaRec;
    private final String communicationPb;
    private final String communicationRec;

    @Override
    public void execute() {
        var windowsSndCtrlOpt = CdiHelper.getOptionalBean(SndCtrlWindows.class);
        if (windowsSndCtrlOpt.isEmpty()) {
            log.warn("The default device (advanced) command is only available on Windows");
            return;
        }
        var windowsSndCtrl = windowsSndCtrlOpt.get();
        windowsSndCtrl.setDefaultDevice(mediaPb, DataFlow.dfRender, Role.roleMultimedia);
        windowsSndCtrl.setDefaultDevice(mediaRec, DataFlow.dfCapture, Role.roleMultimedia);
        windowsSndCtrl.setDefaultDevice(communicationPb, DataFlow.dfRender, Role.roleCommunications);
        windowsSndCtrl.setDefaultDevice(communicationRec, DataFlow.dfCapture, Role.roleCommunications);
    }

    @Nullable
    @Override
    public String getOverlayText() {
        return name;
    }

    @Override
    public String buildLabel() {
        return name;
    }
}
