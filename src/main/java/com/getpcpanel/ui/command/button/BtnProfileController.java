package com.getpcpanel.ui.command.button;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandProfile;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.ButtonCommandController;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.CommandContext;

import javafx.beans.Observable;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
@Cmd(name = "Profile", fxml = "Profile", cmds = CommandProfile.class)
public class BtnProfileController extends ButtonCommandController<CommandProfile> {
    private DeviceSave deviceSave;

    @FXML private ChoiceBox<Profile> profileDropdown;

    @Override
    public void postInit(CommandContext context) {
        deviceSave = context.deviceSave();

        var curProfile = context.profile().getName();
        StreamEx.of(deviceSave.getProfiles()).removeBy(Profile::getName, curProfile).toListAndThen(profileDropdown.getItems()::addAll);
    }

    @Override
    public void initFromCommand(CommandProfile cmd) {
        deviceSave.getProfile(cmd.getProfile()).ifPresent(profile -> profileDropdown.setValue(profile));
        super.initFromCommand(cmd);
    }

    @Override
    public Command buildCommand() {
        return new CommandProfile(profileDropdown.getValue() == null ? null : profileDropdown.getValue().getName());
    }

    @Override
    protected Observable[] determineDependencies() {
        return new Observable[] { profileDropdown.valueProperty() };
    }
}
