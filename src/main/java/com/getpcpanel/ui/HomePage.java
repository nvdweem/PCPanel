package com.getpcpanel.ui;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.getpcpanel.device.Device;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.hid.DeviceScanner;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.util.VersionChecker;

import jakarta.annotation.PostConstruct;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Component
@RequiredArgsConstructor
public class HomePage extends Application {
    private static final String TITLE_FORMAT = "PCPanel Controller %s";
    private final FxHelper fxHelper;
    private final SaveService saveService;
    private final DeviceScanner deviceScanner;
    private final DeviceHolder devices;

    @Value("${application.version}") private String version;
    @Value("${application.build}") private String build;

    @FXML private Pane deviceHolder;
    @FXML private Pane titleHolder;
    @FXML private Pane hintHolder;
    @FXML private Pane lightingButtonHolder;
    @FXML private Pane profileHolder;
    @FXML private Button close;
    @FXML private Button min;
    @FXML private Button deviceListToggle;
    @FXML private Button settings;
    @FXML private Label versionLabel;
    @FXML private VBox labelTarget;
    @FXML private Label noDevicesLabel;
    @FXML private Label hintLabel;
    @FXML private ListView<Device> connectedDeviceList;
    @FXML private Slider globalBrightness;
    public static Stage stage;
    private Pane pane;
    private static HomePage window;

    @Override
    @PostConstruct
    public void init() {
        if (window != null) {
            log.error("Error 2 windows");
            return;
        }
        window = this;
    }

    public void start(Stage stage, boolean quiet) throws Exception {
        start(stage);
        if (!quiet)
            stage.show();
    }

    @Override
    public void start(Stage stage) throws Exception {
        HomePage.stage = stage;
        var loader = fxHelper.getLoader(getClass().getResource("/assets/HomePage.fxml"));
        loader.setController(this);
        pane = loader.load();
        pane.setId("pane");
        var scene = new Scene(pane, 1000.0D, 870.0D);
        showHint(false);
        initWindow();
        scene.getStylesheets().addAll(getClass().getResource("/assets/1.css").toExternalForm());
        stage.getIcons().add(new Image(getClass().getResource("/assets/256x256.png").toExternalForm()));
        stage.setScene(scene);
        ResizeHelper.addResizeListener(stage, 200.0D, 200.0D);
        Platform.setImplicitExit(false);
        stage.initStyle(StageStyle.UNDECORATED);
        stage.sizeToScene();
        stage.setTitle(TITLE_FORMAT.formatted(version));
        addBrightnessListener();

        deviceScanner.init();
    }

    private void addBrightnessListener() {
        globalBrightness.valueProperty().addListener((observable, oldValue, newValue) -> {
            var device = connectedDeviceList.getSelectionModel().getSelectedItem();
            var serialNumber = device.getSerialNumber();
            var cfg = saveService.get().getDeviceSave(serialNumber).getLightingConfig();
            cfg.setGlobalBrightness(newValue.byteValue());
            device.setLighting(device.getLightingConfig(), true);
            saveService.debouncedSave();
        });
    }

    private String buildVersion() {
        return version + (StringUtils.containsIgnoreCase(version, "snapshot") ? " (" + build + ")" : "");
    }

    public static void showHint(boolean show) {
        if (show) {
            if (!window.hintHolder.getChildren().contains(window.hintLabel))
                window.hintHolder.getChildren().add(window.hintLabel);
        } else
            window.hintHolder.getChildren().remove(window.hintLabel);
    }

    @EventListener(ShowMainEvent.class)
    public void reopen() {
        Platform.runLater(() -> {
            stage.show();
            stage.setIconified(false);
            stage.toFront();
        });
    }

    @EventListener
    public void onDeviceConnected(DeviceScanner.DeviceConnectedEvent event) {
        Platform.runLater(() -> addDeviceToUI(devices.getDevice(event.serialNum())));
    }

    @EventListener
    public void onDeviceDisconnected(DeviceScanner.DeviceDisconnectedEvent event) {
        Platform.runLater(() -> StreamEx.of(connectedDeviceList.getItems()).filterBy(Device::getSerialNumber, event.serialNum()).findFirst().ifPresent(connectedDeviceList.getItems()::remove));
    }

    private void addDeviceToUI(Device device) {
        device.setLighting(device.getLightingConfig(), true);
        if (devices.size() == 2)
            setConnectedDeviceListVisible(true);
        connectedDeviceList.getItems().add(device);
        connectedDeviceList.getSelectionModel().select(device);
    }

    private boolean isConnectedDeviceListVisisble() {
        return pane.getChildren().contains(connectedDeviceList);
    }

    private void setConnectedDeviceListVisible(boolean vis) {
        if (vis) {
            if (!pane.getChildren().contains(connectedDeviceList))
                pane.getChildren().add(0, connectedDeviceList);
        } else {
            pane.getChildren().remove(connectedDeviceList);
        }
    }

    private void initWindow() {
        connectedDeviceList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (!deviceHolder.getChildren().isEmpty())
                deviceHolder.getChildren().clear();
            if (!titleHolder.getChildren().isEmpty())
                titleHolder.getChildren().clear();
            if (!lightingButtonHolder.getChildren().isEmpty())
                lightingButtonHolder.getChildren().clear();
            if (!profileHolder.getChildren().isEmpty())
                profileHolder.getChildren().clear();
            if (newValue == null) {
                titleHolder.getChildren().add(noDevicesLabel);
            } else {
                deviceHolder.getChildren().add(newValue.getDevicePane());
                titleHolder.getChildren().add(newValue.getLabel());
                lightingButtonHolder.getChildren().add(newValue.getLightingButton());
                profileHolder.getChildren().add(newValue.getProfileMenu());
                globalBrightness.setValue(newValue.getLightingConfig().getGlobalBrightness());
            }
        });
        connectedDeviceList.setCellFactory(DeviceCell.buildFactory(saveService));
        connectedDeviceList.setEditable(true);
        setConnectedDeviceListVisible(false);
        var apex = Font.loadFont(getClass().getResourceAsStream("/assets/apex-mk2.regular.otf"), 50.0D);
        noDevicesLabel.setFont(apex);
        close.setOnAction(e -> stage.hide());
        settings.setOnAction(e -> {
            try {
                var sd = fxHelper.buildSettingsDialog(stage);
                var childDialogStage = new Stage();
                sd.start(childDialogStage);
            } catch (Exception ex) {
                log.error("Unable to open settings dialog", ex);
            }
        });
        min.setOnAction(e -> stage.setIconified(true));
        var icon = new Region();
        icon.setId("icon");
        min.setGraphic(icon);
        deviceListToggle.setOnAction(e -> setConnectedDeviceListVisible(!isConnectedDeviceListVisisble()));
        versionLabel.setText(TITLE_FORMAT.formatted(buildVersion()));
    }

    @EventListener
    public void newVersionAvailable(VersionChecker.NewVersionAvailableEvent event) {
        var label = new Label("New version available: " + event.version());
        label.setStyle("-fx-text-fill: #ff8888; -fx-font-weight: bold;");
        label.setOnMouseClicked(e -> getHostServices().showDocument(event.url()));
        Platform.runLater(() -> labelTarget.getChildren().add(label));
    }

    @EventListener
    public void onSaveEvent(SaveService.SaveEvent event) {
        Platform.runLater(() -> {
            showHint(event.isNew());

            var selectedDevice = connectedDeviceList.getSelectionModel().getSelectedItem();
            if (selectedDevice != null) {
                globalBrightness.setValue(selectedDevice.getLightingConfig().getGlobalBrightness());
            }
        });
    }

    public record ShowMainEvent() {
    }
}
