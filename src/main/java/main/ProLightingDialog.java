package main;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import colorpicker.ColorDialog;
import colorpicker.HueSlider;
import hid.DeviceScanner;
import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import save.LightingConfig;
import save.LightingConfig.LightingMode;
import save.Save;
import save.SingleKnobLightingConfig;
import save.SingleKnobLightingConfig.SINGLE_KNOB_MODE;
import save.SingleLogoLightingConfig;
import save.SingleLogoLightingConfig.SINGLE_LOGO_MODE;
import save.SingleSliderLabelLightingConfig;
import save.SingleSliderLabelLightingConfig.SINGLE_SLIDER_LABEL_MODE;
import save.SingleSliderLightingConfig;
import save.SingleSliderLightingConfig.SINGLE_SLIDER_MODE;
import util.Util;

public class ProLightingDialog extends Application implements Initializable {
    private Scene scene;

    private Stage stage;

    @FXML
    private TabPane mainPane;

    @FXML
    private TabPane knobsTabbedPane;

    @FXML
    private TabPane slidersTabbedPane;

    @FXML
    private TabPane sliderLabelsTabbedPane;

    @FXML
    private TabPane logoTabPane;

    @FXML
    private TabPane fullBodyTabbedPane;

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
    private Button applyToAllButton;

    private ColorDialog allKnobColor;

    private static final int NUM_KNOBS = 5;

    private static final int NUM_SLIDERS = 4;

    private final TabPane[] knobSingleTabPane = new TabPane[5];

    private final TabPane[] sliderSingleTabPane = new TabPane[4];

    private final TabPane[] sliderLabelSingleTabPane = new TabPane[4];

    private final ColorDialog[] knobStaticCDs = new ColorDialog[5];

    private final ColorDialog[] knobVolumeGradientCD1 = new ColorDialog[5];

    private final ColorDialog[] knobVolumeGradientCD2 = new ColorDialog[5];

    private final ColorDialog[] sliderStaticCDs = new ColorDialog[4];

    private final ColorDialog[] sliderStaticGradientTopCD = new ColorDialog[4];

    private final ColorDialog[] sliderStaticGradientBottomCD = new ColorDialog[4];

    private final ColorDialog[] sliderVolumeGradientCD1 = new ColorDialog[4];

    private final ColorDialog[] sliderVolumeGradientCD2 = new ColorDialog[4];

    private final ColorDialog[] sliderLabelStaticCDs = new ColorDialog[4];

    private ColorDialog logoStaticColor;

    @FXML
    private Slider logoRainbowSpeed;

    @FXML
    private Slider logoRainbowBrightness;

    private HueSlider logoBreathHue;

    @FXML
    private VBox logoBreathBox;

    @FXML
    private Slider logoBreathBrightness;

    @FXML
    private Slider logoBreathSpeed;

    private final Device device;

    private final LightingConfig ogConfig;

    private boolean pressedOk;

    public ProLightingDialog(Device device) {
        this.device = device;
        ogConfig = device.getLightingConfig();
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/assets/ProLightingDialog.fxml"));
        loader.setController(this);
        Pane mainPane = null;
        try {
            mainPane = loader.load();
        } catch (IOException e) {
            e.printStackTrace();
        }
        scene = new Scene(mainPane);
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
        System.err.println(stage.getWidth() + " " + stage.getHeight());
        pressedOk = true;
        Save.saveFile();
        stage.close();
    }

    @FXML
    private void turnOffLights(ActionEvent event) {
        allKnobColor.setCustomColor(Color.WHITE);
        allKnobColor.setCustomColor(Color.BLACK);
        mainPane.getSelectionModel().select(0);
        fullBodyTabbedPane.getSelectionModel().select(0);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        int i;
        for (i = 0; i < 5; i++) {
            int knob = i + 1;
            Tab tab = new Tab("Knob " + knob);
            ColorDialog cd = new ColorDialog(Color.BLACK);
            knobStaticCDs[i] = cd;
            knobVolumeGradientCD1[i] = new ColorDialog();
            knobVolumeGradientCD2[i] = new ColorDialog();
            GridPane volGradientGP = makeFourPanelGridPane("Color when volume is 100", "Color when volume is 0",
                    knobVolumeGradientCD2[i], knobVolumeGradientCD1[i]);
            VBox vbox = new VBox(volGradientGP);
            Tab staticTab = new Tab("Static", cd);
            Tab volGradient = new Tab("Volume Gradient", vbox);
            TabPane singleKnobTabPane = new TabPane(staticTab, volGradient);
            knobSingleTabPane[i] = singleKnobTabPane;
            Util.adjustTabs(singleKnobTabPane, 140, 30);
            singleKnobTabPane.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
            singleKnobTabPane.setSide(Side.LEFT);
            tab.setContent(singleKnobTabPane);
            knobsTabbedPane.getTabs().add(tab);
        }
        for (i = 0; i < 4; i++) {
            Tab tab = new Tab("Slider " + (i + 1));
            ColorDialog cd = new ColorDialog(Color.BLACK);
            sliderStaticCDs[i] = cd;
            sliderStaticGradientTopCD[i] = new ColorDialog();
            sliderStaticGradientBottomCD[i] = new ColorDialog();
            GridPane staticGradientGP = makeFourPanelGridPane("Top Color", "Bottom Color", sliderStaticGradientTopCD[i], sliderStaticGradientBottomCD[i]);
            sliderVolumeGradientCD1[i] = new ColorDialog();
            sliderVolumeGradientCD2[i] = new ColorDialog();
            GridPane volGradientGP = makeFourPanelGridPane("Color when volume is 100", "Color when volume is 0",
                    sliderVolumeGradientCD2[i], sliderVolumeGradientCD1[i]);
            Tab staticTab = new Tab("Static", cd);
            Tab staticGradient = new Tab("Static Gradient", staticGradientGP);
            Tab volGradient = new Tab("Volume Gradient", volGradientGP);
            TabPane singleSliderTabPane = new TabPane(staticTab, staticGradient, volGradient);
            sliderSingleTabPane[i] = singleSliderTabPane;
            Util.adjustTabs(singleSliderTabPane, 140, 30);
            singleSliderTabPane.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
            singleSliderTabPane.setSide(Side.LEFT);
            tab.setContent(singleSliderTabPane);
            slidersTabbedPane.getTabs().add(tab);
        }
        for (i = 0; i < 4; i++) {
            Tab tab = new Tab("Slider " + (i + 1));
            sliderLabelStaticCDs[i] = new ColorDialog();
            Tab staticTab = new Tab("Static", sliderLabelStaticCDs[i]);
            TabPane singleSliderLabelTabPane = new TabPane(staticTab);
            sliderLabelSingleTabPane[i] = singleSliderLabelTabPane;
            Util.adjustTabs(singleSliderLabelTabPane, 140, 30);
            singleSliderLabelTabPane.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
            singleSliderLabelTabPane.setSide(Side.LEFT);
            tab.setContent(singleSliderLabelTabPane);
            sliderLabelsTabbedPane.getTabs().add(tab);
        }
        Util.adjustTabs(fullBodyTabbedPane, 120, 30);
        Util.adjustTabs(logoTabPane, 120, 30);
        logoStaticColor = new ColorDialog();
        logoTabPane.getTabs().get(0).setContent(logoStaticColor);
        allKnobColor = new ColorDialog();
        fullBodyTabbedPane.getTabs().get(0).setContent(allKnobColor);
        Slider[] allSliders = {
                rainbowPhaseShift, rainbowBrightness, rainbowSpeed,
                waveBrightness, waveSpeed,
                breathBrightness, breathSpeed,
                logoRainbowBrightness, logoRainbowSpeed,
                logoBreathBrightness,
                logoBreathSpeed };
        CheckBox[] allCheckBoxes = { rainbowReverse,
                waveReverse, waveBounce };
        waveHue = new HueSlider();
        wavebox.getChildren().add(1, waveHue);
        breathHue = new HueSlider();
        breathbox.getChildren().add(1, breathHue);
        logoBreathHue = new HueSlider();
        logoBreathBox.getChildren().add(1, logoBreathHue);
        applyToAllButton.setOnAction(e -> {
            LightingConfig config = device.getLightingConfig();
            if (mainPane.getSelectionModel().getSelectedIndex() == 1) {
                int knobIndex = knobsTabbedPane.getSelectionModel().getSelectedIndex();
                for (int idx = 0; idx < config.getKnobConfigs().length; idx++) {
                    if (idx != knobIndex)
                        config.getKnobConfigs()[idx].set(config.getKnobConfigs()[knobIndex]);
                }
            } else if (mainPane.getSelectionModel().getSelectedIndex() == 2) {
                int index = slidersTabbedPane.getSelectionModel().getSelectedIndex();
                for (int idx = 0; idx < config.getSliderConfigs().length; idx++) {
                    if (idx != index)
                        config.getSliderConfigs()[idx].set(config.getSliderConfigs()[index]);
                }
            } else if (mainPane.getSelectionModel().getSelectedIndex() == 3) {
                int index = sliderLabelsTabbedPane.getSelectionModel().getSelectedIndex();
                for (int idx = 0; idx < config.getSliderLabelConfigs().length; idx++) {
                    if (idx != index)
                        config.getSliderLabelConfigs()[idx].set(config.getSliderLabelConfigs()[index]);
                }
            }
            initFields();
        });
        initFields();
        initListeners(allSliders, allCheckBoxes);
    }

    private void initFields() {
        LightingConfig config = device.getLightingConfig();
        LightingMode mode = config.getLightingMode();
        if (mode == LightingMode.ALL_COLOR) {
            mainPane.getSelectionModel().select(0);
            fullBodyTabbedPane.getSelectionModel().select(0);
            allKnobColor.setCustomColor(Color.web(config.getAllColor()));
        } else if (mode == LightingMode.ALL_RAINBOW) {
            mainPane.getSelectionModel().select(0);
            fullBodyTabbedPane.getSelectionModel().select(1);
            rainbowPhaseShift.setValue(config.getRainbowPhaseShift() & 0xFF);
            rainbowBrightness.setValue(config.getRainbowBrightness() & 0xFF);
            rainbowSpeed.setValue(config.getRainbowSpeed() & 0xFF);
            rainbowReverse.setSelected(config.getRainbowReverse() == 1);
        } else if (mode == LightingMode.ALL_WAVE) {
            mainPane.getSelectionModel().select(0);
            fullBodyTabbedPane.getSelectionModel().select(2);
            waveHue.setHue(config.getWaveHue() & 0xFF);
            waveBrightness.setValue(config.getWaveBrightness() & 0xFF);
            waveSpeed.setValue(config.getWaveSpeed() & 0xFF);
            waveReverse.setSelected(config.getWaveReverse() == 1);
            waveBounce.setSelected(config.getWaveBounce() == 1);
        } else if (mode == LightingMode.ALL_BREATH) {
            mainPane.getSelectionModel().select(0);
            fullBodyTabbedPane.getSelectionModel().select(3);
            breathHue.setHue(config.getBreathHue() & 0xFF);
            breathBrightness.setValue(config.getBreathBrightness() & 0xFF);
            breathSpeed.setValue(config.getBreathSpeed() & 0xFF);
        } else if (mode == LightingMode.CUSTOM) {
            if (mainPane.getSelectionModel().getSelectedIndex() == 0)
                mainPane.getSelectionModel().select(1);
            SingleKnobLightingConfig[] knobConfigs = config.getKnobConfigs();
            SingleSliderLabelLightingConfig[] sliderLabelConfigs = config.getSliderLabelConfigs();
            SingleSliderLightingConfig[] sliderConfigs = config.getSliderConfigs();
            SingleLogoLightingConfig logoConfig = config.getLogoConfig();
            int i;
            for (i = 0; i < 5; i++) {
                SingleKnobLightingConfig knobConfig = knobConfigs[i];
                if (knobConfig.getMode() == SINGLE_KNOB_MODE.STATIC) {
                    knobSingleTabPane[i].getSelectionModel().select(0);
                    knobStaticCDs[i].setCustomColor(Color.web(knobConfig.getColor1()));
                } else if (knobConfig.getMode() == SINGLE_KNOB_MODE.VOLUME_GRADIENT) {
                    knobSingleTabPane[i].getSelectionModel().select(1);
                    knobVolumeGradientCD1[i].setCustomColor(Color.web(knobConfig.getColor1()));
                    knobVolumeGradientCD2[i].setCustomColor(Color.web(knobConfig.getColor2()));
                }
            }
            for (i = 0; i < 4; i++) {
                SingleSliderLabelLightingConfig sliderLabelConfig = sliderLabelConfigs[i];
                if (sliderLabelConfig.getMode() == SINGLE_SLIDER_LABEL_MODE.STATIC) {
                    sliderLabelSingleTabPane[i].getSelectionModel().select(0);
                    sliderLabelStaticCDs[i].setCustomColor(Color.web(sliderLabelConfig.getColor()));
                }
            }
            for (i = 0; i < 4; i++) {
                SingleSliderLightingConfig sliderConfig = sliderConfigs[i];
                if (sliderConfig.getMode() == SINGLE_SLIDER_MODE.STATIC) {
                    sliderSingleTabPane[i].getSelectionModel().select(0);
                    sliderStaticCDs[i].setCustomColor(Color.web(sliderConfig.getColor1()));
                } else if (sliderConfig.getMode() == SINGLE_SLIDER_MODE.STATIC_GRADIENT) {
                    sliderSingleTabPane[i].getSelectionModel().select(1);
                    sliderStaticGradientBottomCD[i].setCustomColor(Color.web(sliderConfig.getColor1()));
                    sliderStaticGradientTopCD[i].setCustomColor(Color.web(sliderConfig.getColor2()));
                } else if (sliderConfig.getMode() == SINGLE_SLIDER_MODE.VOLUME_GRADIENT) {
                    sliderSingleTabPane[i].getSelectionModel().select(2);
                    sliderVolumeGradientCD1[i].setCustomColor(Color.web(sliderConfig.getColor1()));
                    sliderVolumeGradientCD2[i].setCustomColor(Color.web(sliderConfig.getColor2()));
                }
            }
            if (logoConfig.getMode() == SINGLE_LOGO_MODE.STATIC) {
                logoTabPane.getSelectionModel().select(0);
                logoStaticColor.setCustomColor(Color.web(logoConfig.getColor()));
            } else if (logoConfig.getMode() == SINGLE_LOGO_MODE.RAINBOW) {
                logoTabPane.getSelectionModel().select(1);
                logoRainbowBrightness.setValue(logoConfig.getBrightness() & 0xFF);
                logoRainbowSpeed.setValue(logoConfig.getSpeed() & 0xFF);
            } else if (logoConfig.getMode() == SINGLE_LOGO_MODE.BREATH) {
                logoTabPane.getSelectionModel().select(2);
                logoBreathHue.setHue(logoConfig.getHue() & 0xFF);
                logoBreathBrightness.setValue(logoConfig.getBrightness() & 0xFF);
                logoBreathSpeed.setValue(logoConfig.getSpeed() & 0xFF);
            }
        }
        updateApplyToAllButton();
    }

    private void addListener(ColorDialog[]... xs) {
        byte b;
        int i;
        ColorDialog[][] arrayOfColorDialog;
        for (i = (arrayOfColorDialog = xs).length, b = 0; b < i; ) {
            ColorDialog[] x = arrayOfColorDialog[b];
            byte b1;
            int j;
            ColorDialog[] arrayOfColorDialog1;
            for (j = (arrayOfColorDialog1 = x).length, b1 = 0; b1 < j; ) {
                ColorDialog cd = arrayOfColorDialog1[b1];
                cd.customColorProperty().addListener((a, bb, c) -> updateColors());
                b1++;
            }
            b++;
        }
    }

    private void addListener(TabPane[]... xs) {
        byte b;
        int i;
        TabPane[][] arrayOfTabPane;
        for (i = (arrayOfTabPane = xs).length, b = 0; b < i; ) {
            TabPane[] x = arrayOfTabPane[b];
            byte b1;
            int j;
            TabPane[] arrayOfTabPane1;
            for (j = (arrayOfTabPane1 = x).length, b1 = 0; b1 < j; ) {
                TabPane cd = arrayOfTabPane1[b1];
                cd.getSelectionModel().selectedItemProperty().addListener((a, bb, c) -> updateColors());
                b1++;
            }
            b++;
        }
    }

    private void addListener(TabPane... tbs) {
        byte b;
        int i;
        TabPane[] arrayOfTabPane;
        for (i = (arrayOfTabPane = tbs).length, b = 0; b < i; ) {
            TabPane tb = arrayOfTabPane[b];
            tb.getSelectionModel().selectedItemProperty().addListener((a, bb, c) -> updateColors());
            b++;
        }
    }

    private void initListeners(Slider[] allSliders, CheckBox[] allCheckBoxes) {
        addListener(knobStaticCDs, knobVolumeGradientCD1, knobVolumeGradientCD2,
                sliderStaticCDs, sliderStaticGradientBottomCD, sliderStaticGradientTopCD, sliderVolumeGradientCD1, sliderVolumeGradientCD2,
                sliderLabelStaticCDs);
        addListener(knobSingleTabPane, sliderLabelSingleTabPane, sliderSingleTabPane);
        logoStaticColor.customColorProperty().addListener((a, b, c) -> updateColors());
        allKnobColor.customColorProperty().addListener((observable, oldValue, newValue) -> {
            ColorDialog[] arrayOfColorDialog;
            int i = (arrayOfColorDialog = knobStaticCDs).length;
            for (byte b = 0; b < i; b++) {
                ColorDialog cd = arrayOfColorDialog[b];
                cd.setCustomColor(newValue);
            }
            updateColors();
        });
        addListener(logoTabPane, fullBodyTabbedPane);
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
        waveHue.getHueProperty().addListener((observable, oldValue, newValue) -> updateColors());
        breathHue.getHueProperty().addListener((observable, oldValue, newValue) -> updateColors());
        logoBreathHue.getHueProperty().addListener((observable, oldValue, newValue) -> updateColors());
        mainPane.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            updateColors();
            updateApplyToAllButton();
        });
    }

    private void updateApplyToAllButton() {
        if (mainPane.getSelectionModel().getSelectedIndex() == 0 || mainPane.getSelectionModel().getSelectedIndex() == 4) {
            applyToAllButton.setVisible(false);
            return;
        }
        applyToAllButton.setVisible(true);
        if (mainPane.getSelectionModel().getSelectedIndex() == 1) {
            applyToAllButton.setText("Apply To All Knobs");
        } else if (mainPane.getSelectionModel().getSelectedIndex() == 2) {
            applyToAllButton.setText("Apply To All Sliders");
        } else if (mainPane.getSelectionModel().getSelectedIndex() == 3) {
            applyToAllButton.setText("Apply To All Slider Labels");
        }
    }

    private void updateColors() {
        if (mainPane.getSelectionModel().getSelectedIndex() == 0) {
            if (fullBodyTabbedPane.getSelectionModel().getSelectedIndex() == 0) {
                LightingConfig config = LightingConfig.createAllColor(allKnobColor.getCustomColor());
                device.setLighting(config, false);
            } else if (fullBodyTabbedPane.getSelectionModel().getSelectedIndex() == 1) {
                LightingConfig config = LightingConfig.createRainbowAnimation((byte) (int) rainbowPhaseShift.getValue(), (byte) (int) rainbowBrightness.getValue(),
                        (byte) (int) rainbowSpeed.getValue(), rainbowReverse.isSelected());
                device.setLighting(config, false);
            } else if (fullBodyTabbedPane.getSelectionModel().getSelectedIndex() == 2) {
                LightingConfig config = LightingConfig.createWaveAnimation((byte) waveHue.getHue(), (byte) (int) waveBrightness.getValue(), (byte) (int) waveSpeed.getValue(),
                        waveReverse.isSelected(), waveBounce.isSelected());
                device.setLighting(config, false);
            } else if (fullBodyTabbedPane.getSelectionModel().getSelectedIndex() == 3) {
                LightingConfig config = LightingConfig.createBreathAnimation((byte) breathHue.getHue(), (byte) (int) breathBrightness.getValue(), (byte) (int) breathSpeed.getValue());
                device.setLighting(config, false);
            }
        } else {
            LightingConfig config = new LightingConfig(5, 4);
            config.setLightingMode(LightingMode.CUSTOM);
            for (int knob = 0; knob < 5; knob++) {
                if (knobSingleTabPane[knob].getSelectionModel().getSelectedIndex() == 0) {
                    config.getKnobConfigs()[knob].setMode(SINGLE_KNOB_MODE.STATIC);
                    config.getKnobConfigs()[knob].setColor1(knobStaticCDs[knob].getCustomColor());
                } else if (knobSingleTabPane[knob].getSelectionModel().getSelectedIndex() == 1) {
                    config.getKnobConfigs()[knob].setMode(SINGLE_KNOB_MODE.VOLUME_GRADIENT);
                    config.getKnobConfigs()[knob].setColor1(knobVolumeGradientCD1[knob].getCustomColor());
                    config.getKnobConfigs()[knob].setColor2(knobVolumeGradientCD2[knob].getCustomColor());
                }
            }
            int slider;
            for (slider = 0; slider < 4; slider++) {
                if (sliderLabelSingleTabPane[slider].getSelectionModel().getSelectedIndex() == 0) {
                    config.getSliderLabelConfigs()[slider].setMode(SINGLE_SLIDER_LABEL_MODE.STATIC);
                    config.getSliderLabelConfigs()[slider].setColor(sliderLabelStaticCDs[slider].getCustomColor());
                }
            }
            for (slider = 0; slider < 4; slider++) {
                if (sliderSingleTabPane[slider].getSelectionModel().getSelectedIndex() == 0) {
                    config.getSliderConfigs()[slider].setMode(SINGLE_SLIDER_MODE.STATIC);
                    config.getSliderConfigs()[slider].setColor1(sliderStaticCDs[slider].getCustomColor());
                } else if (sliderSingleTabPane[slider].getSelectionModel().getSelectedIndex() == 1) {
                    config.getSliderConfigs()[slider].setMode(SINGLE_SLIDER_MODE.STATIC_GRADIENT);
                    config.getSliderConfigs()[slider].setColor1(sliderStaticGradientBottomCD[slider].getCustomColor());
                    config.getSliderConfigs()[slider].setColor2(sliderStaticGradientTopCD[slider].getCustomColor());
                } else if (sliderSingleTabPane[slider].getSelectionModel().getSelectedIndex() == 2) {
                    config.getSliderConfigs()[slider].setMode(SINGLE_SLIDER_MODE.VOLUME_GRADIENT);
                    config.getSliderConfigs()[slider].setColor1(sliderVolumeGradientCD1[slider].getCustomColor());
                    config.getSliderConfigs()[slider].setColor2(sliderVolumeGradientCD2[slider].getCustomColor());
                }
            }
            if (logoTabPane.getSelectionModel().getSelectedIndex() == 0) {
                config.getLogoConfig().setMode(SINGLE_LOGO_MODE.STATIC);
                config.getLogoConfig().setColor(logoStaticColor.getCustomColor());
            } else if (logoTabPane.getSelectionModel().getSelectedIndex() == 1) {
                config.getLogoConfig().setMode(SINGLE_LOGO_MODE.RAINBOW);
                config.getLogoConfig().setBrightness((byte) (int) logoRainbowBrightness.getValue());
                config.getLogoConfig().setSpeed((byte) (int) logoRainbowSpeed.getValue());
            } else if (logoTabPane.getSelectionModel().getSelectedIndex() == 2) {
                config.getLogoConfig().setMode(SINGLE_LOGO_MODE.BREATH);
                config.getLogoConfig().setBrightness((byte) (int) logoBreathBrightness.getValue());
                config.getLogoConfig().setSpeed((byte) (int) logoBreathSpeed.getValue());
                config.getLogoConfig().setHue((byte) logoBreathHue.getHue());
            }
            device.setLighting(config, false);
        }
    }

    private static GridPane makeFourPanelGridPane(String str1, String str2, Node obj1, Node obj2) {
        GridPane gp = new GridPane();
        Label l1 = new Label(str1);
        Label l2 = new Label(str2);
        l1.setWrapText(true);
        l2.setWrapText(true);
        gp.addColumn(0, l1, l2);
        gp.addColumn(1, obj1, obj2);
        return gp;
    }
}
