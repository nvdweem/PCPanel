package com.getpcpanel.ui;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.jnativehook.keyboard.NativeKeyEvent;
import org.springframework.stereotype.Component;

import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.spring.OsHelper;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.util.ShortcutHook;

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
public class ProfileSettingsDialog extends Application implements UIInitializer {
    private final SaveService saveService;
    private final ShortcutHook shortcutHook;
    private final OsHelper osHelper;
    @FXML private Pane root;
    private DeviceSave deviceSave;
    private Profile profile;
    private Stage stage;
    @FXML private TextField profileName;
    @FXML private CheckBox mainProfile;
    @FXML private CheckBox focusBackOnLost;
    @FXML private PickProcessesController focusOnListListController;
    @FXML private TextField activationFld;
    @FXML private TitledPane automaticSwitchingPane;

    @Override
    public <T> void initUI(T... args) {
        deviceSave = getUIArg(DeviceSave.class, args, 0);
        profile = getUIArg(Profile.class, args, 1);
        root.setId("pane");
    }

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;
        var scene = new Scene(root, 800.0D, 400.0D);
        scene.getStylesheets().addAll(Objects.requireNonNull(getClass().getResource("/assets/1.css")).toExternalForm());
        stage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResource("/assets/256x256.png")).toExternalForm()));
        stage.setScene(scene);

        initWindow();
        ResizeHelper.addResizeListener(stage, 200.0D, 200.0D);
        stage.sizeToScene();
        stage.setTitle("Profile: " + profile.getName());

        if (osHelper.notWindows()) {
            ((VBox) automaticSwitchingPane.getParent()).getChildren().remove(automaticSwitchingPane);
        }

        stage.show();
    }

    private void initWindow() {
        profileName.setText(profile.getName());
        mainProfile.setSelected(profile.isMainProfile());

        focusBackOnLost.setSelected(profile.isFocusBackOnLost());
        focusOnListListController.setPickType(PickProcessesController.PickType.process).setSelection(profile.getActivateApplications());

        activationFld.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                shortcutHook.setOverrideListener(this::registerShortcut);
            } else {
                shortcutHook.setOverrideListener(null);
            }
        });
        activationFld.setText(StringUtils.defaultString(profile.getActivationShortcut()));
    }

    private void registerShortcut(NativeKeyEvent event) {
        if (shortcutHook.canBeShortcut(event)) {
            activationFld.setText(shortcutHook.toKeyString(event));
        }
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
        profile.setActivationShortcut(StringUtils.trimToNull(activationFld.getText()));

        saveService.save();
        stage.close();
    }

    @FXML
    private void clearActivationShortcut(ActionEvent event) {
        activationFld.setText("");
    }

    @FXML
    private void closeButtonAction(ActionEvent event) {
        stage.close();
    }
}
