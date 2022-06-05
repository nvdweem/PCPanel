package com.getpcpanel.commands.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.ui.HomePage;

import javafx.application.Platform;
import lombok.Getter;
import lombok.ToString;

@ToString(callSuper = true)
public class CommandProfile extends Command implements DeviceAction {
    @Getter private final String profile;

    @JsonCreator
    public CommandProfile(@JsonProperty("profile") String profile) {
        this.profile = profile;
    }

    @Override
    public void execute(String serialNum) {
        Platform.runLater(() -> HomePage.devices.get(serialNum).setProfile(profile));
    }
}
