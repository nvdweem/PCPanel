package com.getpcpanel.ui.command.button;

import java.util.List;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandVolumeApplicationDeviceToggle;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.AdvancedDevices;
import com.getpcpanel.ui.PickProcessesController;
import com.getpcpanel.ui.command.CommandContext;
import com.getpcpanel.ui.command.CommandController;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.RadioButton;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
public class BtnApplicationDeviceToggleController implements CommandController<CommandVolumeApplicationDeviceToggle> {
    @FXML private AdvancedDevices applicationDeviceDevicesController;
    @FXML private PickProcessesController applicationDeviceProcessesController;
    @FXML private RadioButton rdioApplicationDeviceFocus;
    @FXML private RadioButton rdioApplicationDeviceSpecific;

    @Override
    public void postInit(CommandContext context, Command cmd) {
        applicationDeviceProcessesController.setPickType(PickProcessesController.PickType.soundSource);
        applicationDeviceDevicesController.setAllowRemove(true);
        applicationDeviceDevicesController.setOnlyMedia(true);
    }

    @Override
    public void initFromCommand(CommandVolumeApplicationDeviceToggle cmd) {
        rdioApplicationDeviceSpecific.setSelected(!cmd.isFollowFocus());
        rdioApplicationDeviceFocus.setSelected(cmd.isFollowFocus());
        applicationDeviceProcessesController.setSelection(cmd.getProcesses());
        cmd.getDevices().forEach(applicationDeviceDevicesController::add);
    }

    @Override
    public Command buildCommand() {
        var followFocus = rdioApplicationDeviceFocus.isSelected();
        var processes = followFocus ? List.<String>of() : applicationDeviceProcessesController.getSelection();
        return new CommandVolumeApplicationDeviceToggle(processes, followFocus, applicationDeviceDevicesController.getEntries());
    }

    public void addApplicationDevice(ActionEvent ignored) {
        applicationDeviceDevicesController.add();
    }
}
