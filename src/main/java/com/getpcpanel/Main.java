package com.getpcpanel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.getpcpanel.device.Device;
import com.getpcpanel.device.DeviceType;
import com.getpcpanel.device.PCPanelMiniUI;
import com.getpcpanel.device.PCPanelProUI;
import com.getpcpanel.device.PCPanelRGBUI;
import com.getpcpanel.hid.DeviceScanner;
import com.getpcpanel.hid.OutputInterpreter;
import com.getpcpanel.obs.OBSListener;
import com.getpcpanel.profile.Save;
import com.getpcpanel.ui.DeviceCell;
import com.getpcpanel.ui.ResizeHelper;
import com.getpcpanel.ui.SettingsDialog;
import com.getpcpanel.util.FileChecker;
import com.getpcpanel.util.SleepDetector;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class Main extends Application {
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
    @FXML private Label noDevicesLabel;
    @FXML private Label hintLabel;
    @FXML private ListView<Device> connectedDeviceList;
    public static Stage stage;
    private Pane pane;
    private static Main window;
    private static boolean quiet;
    public static volatile boolean saveFileExists;
    public static Map<String, Device> devices = new ConcurrentHashMap<>();
    public static final String VERSION = "2.1.1";
    public static final String TITLE = "PCPanel Software " + VERSION;

    public Main() {
        if (window != null) {
            log.error("Error 2 windows");
            return;
        }
        window = this;
    }

    public static void main(String[] args) {
        if (args.length > 0 && "quiet".equals(args[0]))
            quiet = true;
        FileChecker.checkIsDuplicateRunning();
        TrayWork.tray();
        FileChecker.start();
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        Main.stage = stage;
        var loader = new FXMLLoader(getClass().getResource("/assets/HomePage.fxml"));
        loader.setController(this);
        pane = loader.load();
        pane.setId("pane");
        var scene = new Scene(pane, 1000.0D, 870.0D);
        showHint(false);
        Save.readFile();
        initWindow();
        scene.getStylesheets().addAll(getClass().getResource("/assets/1.css").toExternalForm());
        stage.getIcons().add(new Image("/assets/256x256.png"));
        stage.setScene(scene);
        ResizeHelper.addResizeListener(stage, 200.0D, 200.0D);
        Platform.setImplicitExit(false);
        stage.initStyle(StageStyle.UNDECORATED);
        stage.sizeToScene();
        stage.setTitle(TITLE);
        if (!quiet)
            stage.show();
        OBSListener.start();
        DeviceScanner.start();
        SleepDetector.start();
    }

    @Override
    public void stop() throws Exception {
        OBSListener.stop();
    }

    public static void showHint(boolean show) {
        if (show) {
            if (!window.hintHolder.getChildren().contains(window.hintLabel))
                window.hintHolder.getChildren().add(window.hintLabel);
        } else
            window.hintHolder.getChildren().remove(window.hintLabel);
    }

    public static void reopen() {
        Platform.runLater(() -> {
            stage.show();
            stage.setIconified(false);
            stage.toFront();
        });
    }

    public static void onDeviceConnected(String serialNum, DeviceType dt) {
        Platform.runLater(() -> {
            Device device;
            if (!saveFileExists)
                showHint(true);
            if (!Save.getDevices().containsKey(serialNum))
                Save.createSaveForNewDevice(serialNum, dt);
            if (dt == DeviceType.PCPANEL_RGB) {
                device = new PCPanelRGBUI(serialNum, Save.getDeviceSave(serialNum));
            } else if (dt == DeviceType.PCPANEL_MINI) {
                device = new PCPanelMiniUI(serialNum, Save.getDeviceSave(serialNum));
            } else if (dt == DeviceType.PCPANEL_PRO) {
                device = new PCPanelProUI(serialNum, Save.getDeviceSave(serialNum));
            } else {
                throw new IllegalArgumentException("unknown devicetype: " + dt.name());
            }
            devices.put(serialNum, device);
            OutputInterpreter.sendInit(serialNum);
            window.addDeviceToUI(device);
        });
    }

    public static void onDeviceDisconnected(String serialNum) {
        Platform.runLater(() -> {
            var device = devices.remove(serialNum);
            window.connectedDeviceList.getItems().remove(device);
            device.closeDialogs();
        });
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
            }
        });
        connectedDeviceList.setCellFactory(DeviceCell::new);
        connectedDeviceList.setEditable(true);
        setConnectedDeviceListVisible(false);
        var apex = Font.loadFont(getClass().getResourceAsStream("/assets/apex-mk2.regular.otf"), 50.0D);
        noDevicesLabel.setFont(apex);
        close.setOnAction(e -> stage.hide());
        settings.setOnAction(e -> {
            try {
                var sd = new SettingsDialog(stage);
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
        versionLabel.setText(TITLE);
    }
}
