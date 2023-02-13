package com.getpcpanel.ui.command;

import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.Profile;

import javafx.stage.Stage;

public record CommandContext(Stage stage, DeviceSave deviceSave, Profile profile) {
}
