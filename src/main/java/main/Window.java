package main;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import hid.DeviceScanner;
import hid.OutputInterpreter;
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
import obs.OBSListener;
import save.Save;
import util.FileChecker;
import util.SleepDetector;

public class Window extends Application {
    @FXML
    private Pane deviceHolder;

    @FXML
    private Pane titleHolder;

    @FXML
    private Pane hintHolder;

    @FXML
    private Pane lightingButtonHolder;

    @FXML
    private Pane profileHolder;

    @FXML
    private Button close;

    @FXML
    private Button min;

    @FXML
    private Button deviceListToggle;

    @FXML
    private Button settings;

    @FXML
    private Label versionLabel;

    @FXML
    private Label noDevicesLabel;

    @FXML
    private Label hintLabel;

    @FXML
    private ListView<Device> connectedDeviceList;

    public static Stage stage;

    private Pane pane;

    private static Window window;

    private static boolean quiet;

    public static volatile boolean saveFileExists;

    public static Map<String, Device> devices = new ConcurrentHashMap<>();

    public static final String VERSION = "2.1.1";

    public static final String TITLE = "PCPanel Software 2.1.1";

    public Window() {
        if (window != null) {
            System.err.println("Error 2 windows");
            return;
        }
        window = this;
    }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("quiet"))
            quiet = true;
        FileChecker.checkIsDuplicateRunning();
        TrayWork.tray();
        FileChecker.start();
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        Window.stage = stage;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/assets/HomePage.fxml"));
        loader.setController(this);
        pane = loader.load();
        pane.setId("pane");
        Scene scene = new Scene(pane, 1000.0D, 870.0D);
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
        stage.setTitle("PCPanel Software 2.1.1");
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
            Device device = devices.remove(serialNum);
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
        connectedDeviceList.setCellFactory(c -> new DeviceCell(c));
        connectedDeviceList.setEditable(true);
        setConnectedDeviceListVisible(false);
        Font apex = Font.loadFont(getClass().getResourceAsStream("/assets/apex-mk2.regular.otf"), 50.0D);
        noDevicesLabel.setFont(apex);
        close.setOnAction(e -> stage.hide());
        settings.setOnAction(e -> {
            try {
                SettingsDialog sd = new SettingsDialog(stage);
                Stage childDialogStage = new Stage();
                sd.start(childDialogStage);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        min.setOnAction(e -> stage.setIconified(true));
        Region icon = new Region();
        icon.setId("icon");
        min.setGraphic(icon);
        deviceListToggle.setOnAction(e -> setConnectedDeviceListVisible(!isConnectedDeviceListVisisble()));
        versionLabel.setText("PCPanel Software 2.1.1");
    }
}
