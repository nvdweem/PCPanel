package com.getpcpanel.ui;

import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.spring.OsHelper;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.util.ShortcutHook;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
public class ProfileSettingsDialog extends Application implements UIInitializer {
    private final SaveService saveService;
    private final Optional<ShortcutHook> shortcutHook;
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
    @FXML public VBox osSpecificHolder;
    @FXML public VBox oscBindings;

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

        var toRemove = StreamEx.of(osSpecificHolder.getChildren()).remove(osHelper::isSupported).toSet();
        osSpecificHolder.getChildren().removeAll(toRemove);

        stage.show();
    }

    private void initWindow() {
        profileName.setText(profile.getName());
        mainProfile.setSelected(profile.isMainProfile());

        focusBackOnLost.setSelected(profile.isFocusBackOnLost());
        focusOnListListController.setPickType(PickProcessesController.PickType.process).setSelection(profile.getActivateApplications());

        activationFld.focusedProperty().addListener((observable, oldValue, newValue) -> shortcutHook.ifPresent(hook -> {
                    if (newValue) {
                        hook.setOverrideListener(this::registerShortcut);
                    } else {
                        hook.setOverrideListener(null);
                    }
                }
        ));
        activationFld.setText(StringUtils.defaultString(profile.getActivationShortcut()));
        initOsc();
    }

    private void registerShortcut(NativeKeyEvent event) {
        shortcutHook.ifPresent(hook -> {
            if (hook.canBeShortcut(event)) {
                activationFld.setText(hook.toKeyString(event));
            }
        });
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
        saveOsc();

        saveService.save();
        stage.close();
    }

    private void initOsc() {
        var source = profile.getOscBindings();
        var knobCount = deviceSave.getLightingConfig().getKnobConfigs().length;
        var sliderCount = deviceSave.getLightingConfig().getSliderConfigs().length;

        for (var i = 0; i < knobCount; i++) {
            addOscRow("Knob " + (i + 1), source.getOrDefault(i * 2, ""));
            addOscRow("Button " + (i + 1), source.getOrDefault(i * 2 + 1, ""));
        }

        for (var i = 0; i < sliderCount; i++) {
            addOscRow("Slider " + (i + 1), source.getOrDefault(knobCount + i, ""));
        }
    }

    private void addOscRow(String controlName, String controlValue) {
        var label = new Label(controlName);
        label.setPrefWidth(100);
        var field = new TextField(controlValue);
        field.setPrefWidth(200);
        HBox.setMargin(label, new Insets(0, 10, 0, 0));

        var target = new HBox(label, field);
        target.setAlignment(Pos.CENTER_LEFT);

        oscBindings.getChildren().add(target);
    }

    private void saveOsc() {
        var target = new HashMap<Integer, String>();
        EntryStream.of(oscBindings.getChildren())
                   .selectValues(HBox.class)
                   .mapValues(row -> ((TextField) row.getChildren().get(1)).getText())
                   .forKeyValue(target::put);
        profile.setOscBindings(target);
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
