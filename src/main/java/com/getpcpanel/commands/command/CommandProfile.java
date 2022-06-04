package com.getpcpanel.commands.command;

import com.getpcpanel.Main;

import javafx.application.Platform;
import lombok.Getter;
import lombok.ToString;

@ToString(callSuper = true)
public class CommandProfile extends Command implements DeviceAction {
    @Getter private final String profile;

    public CommandProfile(String profile) {
        this.profile = profile;
    }

    @Override
    public void execute(String serialNum) {
        Platform.runLater(() -> Main.devices.get(serialNum).setProfile(profile));
    }
}
