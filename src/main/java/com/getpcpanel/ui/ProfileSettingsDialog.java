package com.getpcpanel.ui;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.profile.Save;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import one.util.streamex.StreamEx;

public class ProfileSettingsDialog extends Application {
    private Stage stage;
    @FXML private TextField profileName;
    @FXML private CheckBox mainProfile;
    @FXML private VBox applicationRows;
    @FXML private CheckBox focusBackOnLost;
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

        stage.setTitle("Profile: " + profile.name());

        stage.show();
    }

    private void initWindow() {
        profileName.setText(profile.name());
        mainProfile.setSelected(profile.isMainProfile());

        focusBackOnLost.setSelected(profile.focusBackOnLost());
        StreamEx.of(profile.activateApplications()).map(this::createApplicationPane).forEach(applicationRows.getChildren()::add);
        applicationRows.getChildren().add(createApplicationPane(""));
    }

    private void removeEmptyIfNotLast() {
        var toRemove = StreamEx.of(applicationRows.getChildren())
                               .select(Pane.class)
                               .filter(ar -> StringUtils.isBlank(((TextField) ar.getChildren().get(0)).getText()))
                               .filter(ar -> !ar.equals(applicationRows.getChildren().get(applicationRows.getChildren().size() - 1)))
                               .toList();
        toRemove.forEach(applicationRows.getChildren()::remove);
    }

    private void ensureLastEmpty() {
        if (applicationRows.getChildren().isEmpty()) {
            applicationRows.getChildren().add(createApplicationPane(""));
        } else {
            var pane = (Pane) applicationRows.getChildren().get(applicationRows.getChildren().size() - 1);
            if (!StringUtils.isBlank(((TextField) pane.getChildren().get(0)).getText())) {
                applicationRows.getChildren().add(createApplicationPane(""));
            }
        }
    }

    private Pane createApplicationPane(String value) {
        var pane = new HBox();

        var textField = new TextField();
        textField.setPromptText("Application");
        textField.setText(value);

        var button = new Button("...");
        button.setOnAction(e -> {
            UIHelper.showFilePicker("Application", textField);
            ensureLastEmpty();
        });
        textField.setOnKeyReleased(e -> ensureLastEmpty());
        textField.focusedProperty().addListener((o, oldPropertyValue, newPropertyValue) -> {
            if (!newPropertyValue) {
                removeEmptyIfNotLast();
            }
        });

        HBox.setHgrow(textField, Priority.ALWAYS);
        pane.getChildren().add(textField);
        pane.getChildren().add(button);
        return pane;
    }

    @FXML
    private void ok(ActionEvent event) {
        profile.name(profileName.getText());
        profile.isMainProfile(mainProfile.isSelected());
        if (profile.isMainProfile()) {
            StreamEx.of(deviceSave.getProfiles()).remove(profile::equals).forEach(p -> p.isMainProfile(false));
        }

        profile.focusBackOnLost(focusBackOnLost.isSelected());
        profile.activateApplications().clear();
        StreamEx.of(applicationRows.getChildren())
                .select(Pane.class)
                .map(p -> ((TextField) p.getChildren().get(0)).getText())
                .filter(StringUtils::isNotBlank)
                .into(profile.activateApplications());

        Save.saveFile();
        stage.close();
    }

    @FXML
    private void closeButtonAction(ActionEvent event) {
        stage.close();
    }
}
