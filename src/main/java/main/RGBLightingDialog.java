package main;

import colorpicker.ColorDialog;
import colorpicker.HueSlider;
import hid.DeviceScanner;
import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;
import save.LightingConfig;
import save.LightingConfig.LightingMode;
import save.Save;
import util.Util;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

@Log4j2
public class RGBLightingDialog extends Application implements Initializable {

    private Stage stage;

    @FXML
    private TabPane knobsTabbedPane;

    @FXML
    private TabPane allKnobsTabbedPane;

    @FXML
    private Slider rainbowPhaseShift;

    @FXML
    private Slider rainbowBrightness;

    @FXML
    private Slider rainbowSpeed;

    @FXML
    private CheckBox rainbowReverse;

    private HueSlider waveHue;

    @FXML
    private Slider waveBrightness;

    @FXML
    private Slider waveSpeed;

    @FXML
    private CheckBox waveReverse;

    @FXML
    private CheckBox waveBounce;

    private HueSlider breathHue;

    @FXML
    private Slider breathBrightness;

    @FXML
    private Slider breathSpeed;

    @FXML
    private VBox wavebox;

    @FXML
    private VBox breathbox;

    @FXML
    private HBox volumeFollowBox;

    @FXML
    private HBox volumeFollowCheckboxContainer;

    private ColorDialog allKnobColor;

    private final List<ColorDialog> cds = new ArrayList<>();

    private final List<CheckBox> volumeFollowingCheckBoxes = new ArrayList<>();

    private final PCPanelRGBUI device;

    private final LightingConfig ogConfig;

    private boolean pressedOk;

    public RGBLightingDialog(PCPanelRGBUI device) {
        this.device = device;
        ogConfig = device.getLightingConfig();
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/assets/LightingDialog.fxml"));
        loader.setController(this);
        Pane mainPane = null;
        try {
            mainPane = loader.load();
        } catch (IOException e) {
            log.error("Unable to load loader", e);
        }
        Scene scene = new Scene(mainPane);
        scene.getStylesheets().add(getClass().getResource("/assets/dark_theme.css").toExternalForm());
        stage.getIcons().add(new Image("/assets/256x256.png"));
        stage.setOnHiding(e -> {
            if (!pressedOk)
                if (DeviceScanner.CONNECTED_DEVICE_MAP.get(device.getSerialNumber()) == null) {
                    Save.getDeviceSave(device.getSerialNumber()).setLightingConfig(ogConfig);
                } else {
                    device.setLighting(ogConfig, true);
                }
        });
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(Window.stage);
        stage.setScene(scene);
        stage.sizeToScene();
        stage.setTitle("Lighting Dialog");
        stage.show();
    }

    @FXML
    private void onCancel(ActionEvent event) {
        stage.close();
    }

    @FXML
    private void ok(ActionEvent event) {
        log.debug("{} {}", stage.getWidth(), stage.getHeight());
        pressedOk = true;
        Save.saveFile();
        stage.close();
    }

    @FXML
    private void turnOffLights(ActionEvent event) {
        allKnobColor.setCustomColor(Color.WHITE);
        allKnobColor.setCustomColor(Color.BLACK);
        knobsTabbedPane.getSelectionModel().select(0);
        allKnobsTabbedPane.getSelectionModel().select(0);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        for (int i = 0; i < device.getKnobCount(); i++) {
            int knob = i + 1;
            Tab tab = new Tab("Knob " + knob);
            ColorDialog cd = new ColorDialog(Color.BLACK);
            cds.add(cd);
            CheckBox cb = new CheckBox("K" + knob);
            volumeFollowingCheckBoxes.add(cb);
            volumeFollowCheckboxContainer.getChildren().add(cb);
            tab.setContent(cd);
            knobsTabbedPane.getTabs().add(tab);
        }
        Util.adjustTabs(allKnobsTabbedPane, 120, 30);
        allKnobColor = new ColorDialog();
        allKnobsTabbedPane.getTabs().get(0).setContent(allKnobColor);
        Slider[] allSliders = {rainbowPhaseShift, rainbowBrightness, rainbowSpeed,
                waveBrightness, waveSpeed,
                breathBrightness, breathSpeed};
        CheckBox[] allCheckBoxes = {rainbowReverse,
                waveReverse, waveBounce};
        waveHue = new HueSlider();
        wavebox.getChildren().add(1, waveHue);
        breathHue = new HueSlider();
        breathbox.getChildren().add(1, breathHue);
        initFields();
        initListeners(allSliders, allCheckBoxes);
    }

    private void initFields() {
        LightingConfig config = device.getLightingConfig();
        LightingMode mode = config.getLightingMode();
        setFollowingControlsVisible(false);
        if (mode == LightingMode.ALL_COLOR) {
            setFollowingControlsVisible(true);
            setVolumeTrackingData(config.getVolumeBrightnessTrackingEnabled());
            knobsTabbedPane.getSelectionModel().select(0);
            allKnobsTabbedPane.getSelectionModel().select(0);
            Color color = Color.valueOf(config.getAllColor());
            allKnobColor.setCustomColor(color);
            for (ColorDialog cd : cds)
                cd.setCustomColor(color);
        } else if (mode == LightingMode.SINGLE_COLOR) {
            setFollowingControlsVisible(true);
            setVolumeTrackingData(config.getVolumeBrightnessTrackingEnabled());
            knobsTabbedPane.getSelectionModel().select(1);
            for (int i = 0; i < device.getKnobCount(); i++)
                cds.get(i).setCustomColor(Color.valueOf(config.getIndividualColors()[i]));
        } else if (mode == LightingMode.ALL_RAINBOW) {
            knobsTabbedPane.getSelectionModel().select(0);
            allKnobsTabbedPane.getSelectionModel().select(1);
            rainbowPhaseShift.setValue(config.getRainbowPhaseShift() & 0xFF);
            rainbowBrightness.setValue(config.getRainbowBrightness() & 0xFF);
            rainbowSpeed.setValue(config.getRainbowSpeed() & 0xFF);
            rainbowReverse.setSelected(config.getRainbowReverse() == 1);
        } else if (mode == LightingMode.ALL_WAVE) {
            knobsTabbedPane.getSelectionModel().select(0);
            allKnobsTabbedPane.getSelectionModel().select(2);
            waveHue.setHue(config.getWaveHue() & 0xFF);
            waveBrightness.setValue(config.getWaveBrightness() & 0xFF);
            waveSpeed.setValue(config.getWaveSpeed() & 0xFF);
            waveReverse.setSelected(config.getWaveReverse() == 1);
            waveBounce.setSelected(config.getWaveBounce() == 1);
        } else if (mode == LightingMode.ALL_BREATH) {
            knobsTabbedPane.getSelectionModel().select(0);
            allKnobsTabbedPane.getSelectionModel().select(3);
            breathHue.setHue(config.getBreathHue() & 0xFF);
            breathBrightness.setValue(config.getBreathBrightness() & 0xFF);
            breathSpeed.setValue(config.getBreathSpeed() & 0xFF);
        } else {
            log.error("unexpected mode in lightingdialog");
        }
    }

    private void initListeners(Slider[] allSliders, CheckBox[] allCheckBoxes) {
        for (ColorDialog cd : cds)
            cd.customColorProperty().addListener((observable, oldValue, newValue) -> updateColors());
        allKnobColor.customColorProperty().addListener((observable, oldValue, newValue) -> {
            for (ColorDialog cd : cds)
                cd.setCustomColor(newValue);
            updateColors();
        });
        byte b;
        int i;
        Slider[] arrayOfSlider;
        for (i = (arrayOfSlider = allSliders).length, b = 0; b < i; ) {
            Slider slider = arrayOfSlider[b];
            slider.valueProperty().addListener((observable, oldValue, newValue) -> updateColors());
            b++;
        }
        CheckBox[] arrayOfCheckBox;
        for (i = (arrayOfCheckBox = allCheckBoxes).length, b = 0; b < i; ) {
            CheckBox cb = arrayOfCheckBox[b];
            cb.selectedProperty().addListener((observable, oldValue, newValue) -> updateColors());
            b++;
        }
        for (CheckBox cb : volumeFollowingCheckBoxes)
            cb.selectedProperty().addListener((observable, oldValue, newValue) -> updateColors());
        waveHue.getHueProperty().addListener((observable, oldValue, newValue) -> updateColors());
        breathHue.getHueProperty().addListener((observable, oldValue, newValue) -> updateColors());
        knobsTabbedPane.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> updateColors());
        allKnobsTabbedPane.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> updateColors());
    }

    private void updateColors() {
        setFollowingControlsVisible(false);
        if (knobsTabbedPane.getSelectionModel().getSelectedIndex() == 0) {
            if (allKnobsTabbedPane.getSelectionModel().getSelectedIndex() == 0) {
                setFollowingControlsVisible(true);
                LightingConfig config = LightingConfig.createAllColor(allKnobColor.getCustomColor(), getVolumeTrackingData());
                device.setLighting(config, false);
            } else if (allKnobsTabbedPane.getSelectionModel().getSelectedIndex() == 1) {
                LightingConfig config = LightingConfig.createRainbowAnimation((byte) (int) rainbowPhaseShift.getValue(), (byte) (int) rainbowBrightness.getValue(),
                        (byte) (int) rainbowSpeed.getValue(), rainbowReverse.isSelected());
                device.setLighting(config, false);
            } else if (allKnobsTabbedPane.getSelectionModel().getSelectedIndex() == 2) {
                LightingConfig config = LightingConfig.createWaveAnimation((byte) waveHue.getHue(), (byte) (int) waveBrightness.getValue(), (byte) (int) waveSpeed.getValue(),
                        waveReverse.isSelected(), waveBounce.isSelected());
                device.setLighting(config, false);
            } else if (allKnobsTabbedPane.getSelectionModel().getSelectedIndex() == 3) {
                LightingConfig config = LightingConfig.createBreathAnimation((byte) breathHue.getHue(), (byte) (int) breathBrightness.getValue(), (byte) (int) breathSpeed.getValue());
                device.setLighting(config, false);
            }
        } else {
            setFollowingControlsVisible(true);
            Color[] colors = new Color[device.getKnobCount()];
            for (int i = 0; i < device.getKnobCount(); ) {
                colors[i] = cds.get(i).getCustomColor();
                i++;
            }
            LightingConfig config = LightingConfig.createSingleColor(colors, getVolumeTrackingData());
            device.setLighting(config, false);
        }
    }

    private void setFollowingControlsVisible(boolean b) {
        volumeFollowBox.setVisible(b);
    }

    private boolean[] getVolumeTrackingData() {
        boolean[] ret = new boolean[device.getKnobCount()];
        for (int i = 0; i < ret.length; i++)
            ret[i] = volumeFollowingCheckBoxes.get(i).isSelected();
        return ret;
    }

    private void setVolumeTrackingData(boolean[] data) {
        for (int i = 0; i < data.length; i++)
            volumeFollowingCheckBoxes.get(i).setSelected(data[i]);
    }
}
