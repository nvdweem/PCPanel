package com.getpcpanel.ui;

import java.util.Objects;

import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.profile.Save;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import one.util.streamex.StreamEx;

public class ProfileSettingsDialog extends Application {
    private Stage stage;
    @FXML private TextField profileName;
    @FXML private CheckBox mainProfile;
    @FXML private CheckBox focusBackOnLost;
    @FXML private PickProcessesController focusOnListListController;
    private final DeviceSave deviceSave;
    private final Profile profile;

    public ProfileSettingsDialog(DeviceSave deviceSave, Profile profile) {
        this.deviceSave = deviceSave;
        this.profile = profile;
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;

        var loader = new FXMLLoader(getClass().getResource("/assets/ProfileSettingsDialog.fxml"));
        loader.setController(this);
        Pane pane = loader.load();
        pane.setId("pane");
        var scene = new Scene(pane, 800.0D, 300.0D);
        initWindow();
        scene.getStylesheets().addAll(Objects.requireNonNull(getClass().getResource("/assets/1.css")).toExternalForm());
        stage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResource("/assets/256x256.png")).toExternalForm()));
        stage.setScene(scene);
        ResizeHelper.addResizeListener(stage, 200.0D, 200.0D);
        Platform.setImplicitExit(false);
        stage.sizeToScene();

        stage.setTitle("Profile: " + profile.getName());

        stage.show();
    }

    private void initWindow() {
        profileName.setText(profile.getName());
        mainProfile.setSelected(profile.isMainProfile());

        focusBackOnLost.setSelected(profile.isFocusBackOnLost());
        focusOnListListController.setSelection(PickProcessesController.PickType.process, profile.getActivateApplications());
    }

    @FXML
    private void ok(ActionEvent event) {
        profile.setName(profileName.getText());
        profile.setMainProfile(mainProfile.isSelected());
        if (profile.isMainProfile()) {
            StreamEx.of(deviceSave.getProfiles()).remove(profile::equals).forEach(p -> p.setMainProfile(false));
        }

        profile.setFocusBackOnLost(focusBackOnLost.isSelected());
        profile.getActivateApplications().clear();
        profile.setActivateApplications(focusOnListListController.getSelection());

        Save.saveFile();
        stage.close();
    }

    @FXML
    private void closeButtonAction(ActionEvent event) {
        stage.close();
    }
}
