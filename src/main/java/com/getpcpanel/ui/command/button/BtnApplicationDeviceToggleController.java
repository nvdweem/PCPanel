package com.getpcpanel.ui.command.button;

import static com.getpcpanel.spring.OsHelper.WINDOWS;

import java.util.List;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandVolumeApplicationDeviceToggle;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.AdvancedDevices;
import com.getpcpanel.ui.PickProcessesController;
import com.getpcpanel.ui.command.ButtonCommandController;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.CommandContext;

import javafx.beans.Observable;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.RadioButton;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
@Cmd(name = "Application Sound Device", fxml = "ApplicationDeviceToggle", cmds = CommandVolumeApplicationDeviceToggle.class, os = WINDOWS)
public class BtnApplicationDeviceToggleController extends ButtonCommandController<CommandVolumeApplicationDeviceToggle> {
    @FXML private AdvancedDevices applicationDeviceDevicesController;
    @FXML private PickProcessesController applicationDeviceProcessesController;
    @FXML private RadioButton rdioApplicationDeviceFocus;
    @FXML private RadioButton rdioApplicationDeviceSpecific;

    @Override
    public void postInit(CommandContext context) {
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
        super.initFromCommand(cmd);
    }

    @Override
    public Command buildCommand() {
        var followFocus = rdioApplicationDeviceFocus.isSelected();
        var processes = followFocus ? List.<String>of() : applicationDeviceProcessesController.getSelection();
        return new CommandVolumeApplicationDeviceToggle(processes, followFocus, applicationDeviceDevicesController.getEntries());
    }

    @Override
    protected Observable[] determineDependencies() {
        return new Observable[] {
                applicationDeviceProcessesController.getObservable(),
                rdioApplicationDeviceFocus.selectedProperty(), rdioApplicationDeviceSpecific.selectedProperty()
        };
    }

    public void addApplicationDevice(ActionEvent ignored) {
        applicationDeviceDevicesController.add();
    }
}
