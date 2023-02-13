package com.getpcpanel.ui.command.button;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandVolumeDefaultDeviceToggleAdvanced;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.AdvancedDevices;
import com.getpcpanel.ui.command.CommandContext;
import com.getpcpanel.ui.command.CommandController;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
public class BtnDeviceToggleAdvancedController implements CommandController<CommandVolumeDefaultDeviceToggleAdvanced> {
    @FXML private AdvancedDevices defaultDeviceToggleAdvancedController;

    @Override
    public void postInit(CommandContext context, Command cmd) {
        defaultDeviceToggleAdvancedController.setAllowRemove(true);
    }

    @Override
    public void initFromCommand(CommandVolumeDefaultDeviceToggleAdvanced cmd) {
        cmd.getDevices().forEach(defaultDeviceToggleAdvancedController::add);
    }

    @Override
    public Command buildCommand() {
        return new CommandVolumeDefaultDeviceToggleAdvanced(defaultDeviceToggleAdvancedController.getEntries());
    }

    public void addDefaultDeviceToggleAdvanced(ActionEvent ignored) {
        defaultDeviceToggleAdvancedController.add();
    }
}
