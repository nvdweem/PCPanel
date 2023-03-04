package com.getpcpanel.ui.command.button;

import static com.getpcpanel.spring.OsHelper.WINDOWS;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandVolumeDefaultDeviceAdvanced;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.AdvancedDevices;
import com.getpcpanel.ui.command.ButtonCommandController;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.CommandContext;

import javafx.fxml.FXML;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
@Cmd(name = "Default Device Advanced", fxml = "DefaultDeviceAdvanced", cmds = CommandVolumeDefaultDeviceAdvanced.class, os = WINDOWS)
public class BtnDefaultDeviceAdvancedController implements ButtonCommandController<CommandVolumeDefaultDeviceAdvanced> {
    @FXML private AdvancedDevices defaultDeviceAdvancedController;

    @Override
    public void postInit(CommandContext context) {
        defaultDeviceAdvancedController.add();
    }

    @Override
    public void initFromCommand(CommandVolumeDefaultDeviceAdvanced cmd) {
        defaultDeviceAdvancedController.set(cmd.getName(), cmd.getMediaPb(), cmd.getMediaRec(), cmd.getCommunicationPb(), cmd.getCommunicationRec());
    }

    @Override
    public Command buildCommand() {
        var entry = defaultDeviceAdvancedController.getEntries().get(0);
        return new CommandVolumeDefaultDeviceAdvanced(entry.name(), entry.mediaPlayback(), entry.mediaRecord(), entry.communicationPlayback(), entry.communicationRecord());
    }
}
