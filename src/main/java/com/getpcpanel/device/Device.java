package com.getpcpanel.device;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.hid.OutputInterpreter;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.LightingConfig;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.ui.FxHelper;
import com.getpcpanel.ui.LimitedTextField;
import com.getpcpanel.util.ApplicationFocusListener;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
public abstract class Device {
    private static final Image lightingImage = new Image(Objects.requireNonNull(PCPanelProUI.class.getResource("/assets/lighting.png")).toExternalForm());
    @Getter(AccessLevel.PROTECTED) private final FxHelper fxHelper;
    private final SaveService saveService;
    private final OutputInterpreter outputInterpreter;
    private final ApplicationFocusListener.FocusListenerRemover removeFocusListener;
    private HBox profileMenu;
    private ComboBox<Profile> profiles;
    protected String serialNumber;
    protected DeviceSave save;

    protected Device(FxHelper fxHelper, SaveService saveService, OutputInterpreter outputInterpreter, String serialNum, DeviceSave deviceSave) {
        this.fxHelper = fxHelper;
        this.saveService = saveService;
        this.outputInterpreter = outputInterpreter;
        serialNumber = serialNum;
        save = deviceSave;
        initProfileMenu();
        removeFocusListener = ApplicationFocusListener.addFocusListener(this::focusChanged);
    }

    private void initProfileMenu() {
        profileMenu = new HBox();
        profileMenu.setAlignment(Pos.CENTER_RIGHT);
        profiles = new ComboBox<>(FXCollections.observableArrayList(save.getProfiles()));
        profiles.setPrefWidth(400.0D);
        profiles.getSelectionModel().select(save.getCurrentProfile());
        var textfield = new LimitedTextField(10);
        profiles.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null)
                return;
            log.debug("change");
            var name = newValue.getName();
            updateCurrentProfileName(name);
        });
        var buttonCell = new ListCell<Profile>() {
            @Override
            protected void updateItem(Profile item, boolean btl) {
                super.updateItem(item, btl);
                setGraphic(null);
                if (item != null)
                    setText(item.getName());
            }
        };
        textfield.setOnAction(c -> {
            var p = buttonCell.getItem();
            var oldName = p.getName();
            var newName = textfield.getText();
            buttonCell.setGraphic(null);
            if (save.getProfile(newName) != null) {
                buttonCell.setText(oldName);
                return;
            }
            p.setName(newName);
            buttonCell.setText(newName);
            profiles.getItems().set(profiles.getItems().indexOf(p), p);
            saveService.save();
        });
        textfield.focusedProperty().addListener((arg, oldVal, newVal) -> {
            if (!newVal) {
                buttonCell.setGraphic(null);
                buttonCell.setText(buttonCell.getItem().getName());
            }
        });
        textfield.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                buttonCell.setGraphic(null);
                buttonCell.setText(buttonCell.getItem().getName());
            }
        });
        profiles.setButtonCell(buttonCell);
        profiles.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                event.consume();
                var rename = new MenuItem("Rename");
                var delete = new MenuItem("Delete");
                rename.setOnAction(c -> {
                    buttonCell.setGraphic(textfield);
                    textfield.requestFocus();
                    textfield.setText(buttonCell.getText());
                    textfield.selectAll();
                    buttonCell.setText("");
                });
                delete.setOnAction(c -> {
                    save.getProfiles().remove(buttonCell.getItem());
                    profiles.getItems().remove(buttonCell.getItem());
                    if (profiles.getValue() == null)
                        profiles.getSelectionModel().select(0);
                });
                if (profiles.getItems().size() <= 1)
                    delete.setDisable(true);
                var cm = new ContextMenu(rename, delete);
                profiles.setContextMenu(cm);
            } else if (event.getButton() == MouseButton.PRIMARY) {
                buttonCell.getGraphic();
            }
        });

        profileMenu.getChildren().add(buildAddButton());
        profileMenu.getChildren().add(buildSettingsButton());
        profileMenu.getChildren().addAll(profiles);
    }

    private void updateCurrentProfileName(String name) {
        var success = save.setCurrentProfile(name);
        if (!success)
            return;
        setLighting(save.getLightingConfig(), true);
        saveService.save();
    }

    private void focusChanged(String from, String to) {
        if (switchForApplication(to))
            return;

        switchAwayFromApplication(from);
    }

    private boolean switchForApplication(String to) {
        var result = new boolean[] { false };
        save.getProfiles()
            .stream()
            .filter(p -> StreamEx.of(p.getActivateApplications()).anyMatch(i -> StringUtils.equalsIgnoreCase(i, to)))
            .findFirst()
            .ifPresent(p -> {
                Platform.runLater(() -> profiles.getSelectionModel().select(p));
                result[0] = true;
            });
        return result[0];
    }

    private void switchAwayFromApplication(String from) {
        var mainProfile = StreamEx.of(save.getProfiles()).findFirst(Profile::isMainProfile);
        if (!profiles.getSelectionModel().getSelectedItem().isFocusBackOnLost() || mainProfile.isEmpty()) {
            return;
        }
        if (StreamEx.of(profiles.getSelectionModel().getSelectedItem().getActivateApplications()).anyMatch(a -> StringUtils.equalsIgnoreCase(a, from))) {
            Platform.runLater(() -> profiles.getSelectionModel().select(mainProfile.get()));
        }
    }

    private Button buildAddButton() {
        var addButton = new Button();
        var svgCode = "M28,14H18V4c0-1.104-0.896-2-2-2s-2,0.896-2,2v10H4c-1.104,0-2,0.896-2,2s0.896,2,2,2h10v10c0,1.104,0.896,2,2,2  s2-0.896,2-2V18h10c1.104,0,2-0.896,2-2S29.104,14,28,14z";
        var path = new SVGPath();
        path.setStyle("-fx-fill:white;");
        path.setContent(svgCode);
        path.setScaleX(0.7D);
        path.setScaleY(0.7D);
        addButton.setGraphic(path);
        addButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        addButton.setOnAction(c -> {
            String newName;
            for (var i = 1; ; i++) {
                newName = "profile " + i;
                if (save.getProfile(newName) == null)
                    break;
            }
            var newProfile = new Profile(newName, getDeviceType());
            save.getProfiles().add(newProfile);
            profiles.getItems().add(newProfile);
            profiles.getSelectionModel().select(newProfile);
        });
        addButton.setPrefSize(44.0D, 44.0D);
        return addButton;
    }

    private Button buildSettingsButton() {
        var settingSvg = "M24.38,10.175l-2.231-0.268c-0.228-0.851-0.562-1.655-0.992-2.401l1.387-1.763c0.212-0.271,0.188-0.69-0.057-0.934" +
                "l-2.299-2.3c-0.242-0.243-0.662-0.269-0.934-0.057l-1.766,1.389c-0.743-0.43-1.547-0.764-2.396-0.99L14.825,0.62" +
                "C14.784,0.279,14.469,0,14.125,0h-3.252c-0.344,0-0.659,0.279-0.699,0.62L9.906,2.851c-0.85,0.227-1.655,0.562-2.398,0.991" +
                "L5.743,2.455c-0.27-0.212-0.69-0.187-0.933,0.056L2.51,4.812C2.268,5.054,2.243,5.474,2.456,5.746L3.842,7.51" +
                "c-0.43,0.744-0.764,1.549-0.991,2.4l-2.23,0.267C0.28,10.217,0,10.532,0,10.877v3.252c0,0.344,0.279,0.657,0.621,0.699l2.231,0.268" +
                "c0.228,0.848,0.561,1.652,0.991,2.396l-1.386,1.766c-0.211,0.271-0.187,0.69,0.057,0.934l2.296,2.301" +
                "c0.243,0.242,0.663,0.269,0.933,0.057l1.766-1.39c0.744,0.43,1.548,0.765,2.398,0.991l0.268,2.23" +
                "c0.041,0.342,0.355,0.62,0.699,0.62h3.252c0.345,0,0.659-0.278,0.699-0.62l0.268-2.23c0.851-0.228,1.655-0.562,2.398-0.991" +
                "l1.766,1.387c0.271,0.212,0.69,0.187,0.933-0.056l2.299-2.301c0.244-0.242,0.269-0.662,0.056-0.935l-1.388-1.764" +
                "c0.431-0.744,0.764-1.548,0.992-2.397l2.23-0.268C24.721,14.785,25,14.473,25,14.127v-3.252" +
                "C25.001,10.529,24.723,10.216,24.38,10.175z M12.501,18.75c-3.452,0-6.25-2.798-6.25-6.25s2.798-6.25,6.25-6.25" +
                "s6.25,2.798,6.25,6.25S15.954,18.75,12.501,18.75z";
        var setPath = new SVGPath();
        setPath.setStyle("-fx-fill:white;");
        setPath.setContent(settingSvg);
        setPath.setScaleX(.9);
        setPath.setScaleY(.9);

        var settingsButton = new Button();
        settingsButton.setGraphic(setPath);
        settingsButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        settingsButton.setOnAction(c -> {
            try {
                var stage = new Stage();
                var selection = profiles.getSelectionModel().getSelectedItem();
                fxHelper.buildProfileSettingsDialog(save, selection).start(stage);
                stage.setOnHidden(e -> Platform.runLater(() -> {
                    profiles.getButtonCell().setText(selection.getName());
                    if (profiles.getSelectionModel().getSelectedItem().equals(selection)) {
                        updateCurrentProfileName(selection.getName());
                    }
                }));
            } catch (Exception e) {
                log.error("Unable to load profile settings dialog", e);
            }
        });
        settingsButton.setPrefSize(44.0D, 44.0D);
        return settingsButton;
    }

    public Pane getProfileMenu() {
        return profileMenu;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setProfile(String name) {
        var p = save.getProfile(name);
        if (p != null)
            profiles.setValue(p);
    }

    public String getDisplayName() {
        return save.getDisplayName();
    }

    public void setDisplayName(String name) {
        save.setDisplayName(name);
    }

    public LightingConfig getLightingConfig() {
        return save.getLightingConfig();
    }

    public void setLighting(LightingConfig config, boolean priority) {
        if (config == null) {
            config = LightingConfig.defaultLightingConfig(getDeviceType());
            save.setLightingConfig(config);
            saveService.save();
        }
        try {
            showLightingConfigToUI(config);
            save.setLightingConfig(config);
            try {
                outputInterpreter.sendLightingConfig(getSerialNumber(), getDeviceType(), config, priority);
            } catch (Exception e) {
                log.error("Unable to send lighting config", e);
            }
        } catch (Exception e) {
            log.error("Unable to set lighting", e);
            setLighting(LightingConfig.defaultLightingConfig(getDeviceType()), priority);
        }
    }

    protected ImageView getLightingImage() {
        var lightingImageView = new ImageView(lightingImage);
        lightingImageView.setFitWidth(40);
        lightingImageView.setPreserveRatio(true);
        return lightingImageView;
    }

    public abstract Pane getDevicePane();

    public abstract Node getLabel();

    public abstract Button getLightingButton();

    public abstract Image getPreviewImage();

    public abstract DeviceType getDeviceType();

    public abstract void setKnobRotation(int paramInt1, int paramInt2);

    public abstract void setButtonPressed(int paramInt, boolean paramBoolean);

    public abstract void closeDialogs();

    public abstract void showLightingConfigToUI(LightingConfig paramLightingConfig);

    public void disconnected() {
        closeDialogs();
        removeFocusListener.run();
    }
}
