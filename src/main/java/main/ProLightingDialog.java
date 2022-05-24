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
import lombok.extern.log4j.Log4j2;
import save.LightingConfig;
import save.LightingConfig.LightingMode;
import save.Save;
import save.SingleKnobLightingConfig.SINGLE_KNOB_MODE;
import save.SingleLogoLightingConfig.SINGLE_LOGO_MODE;
import save.SingleSliderLabelLightingConfig.SINGLE_SLIDER_LABEL_MODE;
import save.SingleSliderLightingConfig.SINGLE_SLIDER_MODE;
import util.Util;

@Log4j2
public class ProLightingDialog extends Application implements Initializable {
    private Stage stage;
    @FXML private TabPane mainPane;
    @FXML private TabPane knobsTabbedPane;
    @FXML private TabPane slidersTabbedPane;
    @FXML private TabPane sliderLabelsTabbedPane;
    @FXML private TabPane logoTabPane;
    @FXML private TabPane fullBodyTabbedPane;
    @FXML private Slider rainbowPhaseShift;
    @FXML private Slider rainbowBrightness;
    @FXML private Slider rainbowSpeed;
    @FXML private CheckBox rainbowReverse;
    private HueSlider waveHue;
    @FXML private Slider waveBrightness;
    @FXML private Slider waveSpeed;
    @FXML private CheckBox waveReverse;
    @FXML private CheckBox waveBounce;
    private HueSlider breathHue;
    @FXML private Slider breathBrightness;
    @FXML private Slider breathSpeed;
    @FXML private VBox wavebox;
    @FXML private VBox breathbox;
    @FXML private Button applyToAllButton;
    private ColorDialog allKnobColor;
    private static final int NUM_KNOBS = 5;
    private static final int NUM_SLIDERS = 4;
    private final TabPane[] knobSingleTabPane = new TabPane[NUM_KNOBS];
    private final TabPane[] sliderSingleTabPane = new TabPane[NUM_SLIDERS];
    private final TabPane[] sliderLabelSingleTabPane = new TabPane[NUM_SLIDERS];
    private final ColorDialog[] knobStaticCDs = new ColorDialog[NUM_KNOBS];
    private final ColorDialog[] knobVolumeGradientCD1 = new ColorDialog[NUM_KNOBS];
    private final ColorDialog[] knobVolumeGradientCD2 = new ColorDialog[NUM_KNOBS];
    private final ColorDialog[] sliderStaticCDs = new ColorDialog[NUM_SLIDERS];
    private final ColorDialog[] sliderStaticGradientTopCD = new ColorDialog[NUM_SLIDERS];
    private final ColorDialog[] sliderStaticGradientBottomCD = new ColorDialog[NUM_SLIDERS];
    private final ColorDialog[] sliderVolumeGradientCD1 = new ColorDialog[NUM_SLIDERS];
    private final ColorDialog[] sliderVolumeGradientCD2 = new ColorDialog[NUM_SLIDERS];
    private final ColorDialog[] sliderLabelStaticCDs = new ColorDialog[NUM_SLIDERS];
    private ColorDialog logoStaticColor;
    @FXML private Slider logoRainbowSpeed;
    @FXML private Slider logoRainbowBrightness;
    private HueSlider logoBreathHue;
    @FXML private VBox logoBreathBox;
    @FXML private Slider logoBreathBrightness;
    @FXML private Slider logoBreathSpeed;
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
        var loader = new FXMLLoader(getClass().getResource("/assets/ProLightingDialog.fxml"));
        loader.setController(this);
        Pane mainPane = null;
        try {
            mainPane = loader.load();
        } catch (IOException e) {
            log.error("Unable to load loader", e);
        }
        var scene = new Scene(mainPane);
        scene.getStylesheets().add(getClass().getResource("/assets/dark_theme.css").toExternalForm());
        stage.getIcons().add(new Image("/assets/256x256.png"));
        stage.setOnHiding(e -> {
            if (!pressedOk)
                if (DeviceScanner.getConnectedDevice(device.getSerialNumber()) == null) {
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
        mainPane.getSelectionModel().select(0);
        fullBodyTabbedPane.getSelectionModel().select(0);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        int i;
        for (i = 0; i < NUM_KNOBS; i++) {
            var knob = i + 1;
            var tab = new Tab("Knob " + knob);
            var cd = new ColorDialog(Color.BLACK);
            knobStaticCDs[i] = cd;
            knobVolumeGradientCD1[i] = new ColorDialog();
            knobVolumeGradientCD2[i] = new ColorDialog();
            var volGradientGP = makeFourPanelGridPane("Color when volume is 100", "Color when volume is 0",
                    knobVolumeGradientCD2[i], knobVolumeGradientCD1[i]);
            var vbox = new VBox(volGradientGP);
            var staticTab = new Tab("Static", cd);
            var volGradient = new Tab("Volume Gradient", vbox);
            var singleKnobTabPane = new TabPane(staticTab, volGradient);
            knobSingleTabPane[i] = singleKnobTabPane;
            Util.adjustTabs(singleKnobTabPane, 140, 30);
            singleKnobTabPane.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
            singleKnobTabPane.setSide(Side.LEFT);
            tab.setContent(singleKnobTabPane);
            knobsTabbedPane.getTabs().add(tab);
        }
        for (i = 0; i < NUM_SLIDERS; i++) {
            var tab = new Tab("Slider " + (i + 1));
            var cd = new ColorDialog(Color.BLACK);
            sliderStaticCDs[i] = cd;
            sliderStaticGradientTopCD[i] = new ColorDialog();
            sliderStaticGradientBottomCD[i] = new ColorDialog();
            var staticGradientGP = makeFourPanelGridPane("Top Color", "Bottom Color", sliderStaticGradientTopCD[i], sliderStaticGradientBottomCD[i]);
            sliderVolumeGradientCD1[i] = new ColorDialog();
            sliderVolumeGradientCD2[i] = new ColorDialog();
            var volGradientGP = makeFourPanelGridPane("Color when volume is 100", "Color when volume is 0",
                    sliderVolumeGradientCD2[i], sliderVolumeGradientCD1[i]);
            var staticTab = new Tab("Static", cd);
            var staticGradient = new Tab("Static Gradient", staticGradientGP);
            var volGradient = new Tab("Volume Gradient", volGradientGP);
            var singleSliderTabPane = new TabPane(staticTab, staticGradient, volGradient);
            sliderSingleTabPane[i] = singleSliderTabPane;
            Util.adjustTabs(singleSliderTabPane, 140, 30);
            singleSliderTabPane.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
            singleSliderTabPane.setSide(Side.LEFT);
            tab.setContent(singleSliderTabPane);
            slidersTabbedPane.getTabs().add(tab);
        }
        for (i = 0; i < NUM_SLIDERS; i++) {
            var tab = new Tab("Slider " + (i + 1));
            sliderLabelStaticCDs[i] = new ColorDialog();
            var staticTab = new Tab("Static", sliderLabelStaticCDs[i]);
            var singleSliderLabelTabPane = new TabPane(staticTab);
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
        var allSliders = new Slider[] {
                rainbowPhaseShift, rainbowBrightness, rainbowSpeed,
                waveBrightness, waveSpeed,
                breathBrightness, breathSpeed,
                logoRainbowBrightness, logoRainbowSpeed,
                logoBreathBrightness,
                logoBreathSpeed };
        var allCheckBoxes = new CheckBox[] { rainbowReverse,
                waveReverse, waveBounce };
        waveHue = new HueSlider();
        wavebox.getChildren().add(1, waveHue);
        breathHue = new HueSlider();
        breathbox.getChildren().add(1, breathHue);
        logoBreathHue = new HueSlider();
        logoBreathBox.getChildren().add(1, logoBreathHue);
        applyToAllButton.setOnAction(e -> {
            var config = device.getLightingConfig();
            if (mainPane.getSelectionModel().getSelectedIndex() == 1) {
                var knobIndex = knobsTabbedPane.getSelectionModel().getSelectedIndex();
                for (var idx = 0; idx < config.getKnobConfigs().length; idx++) {
                    if (idx != knobIndex)
                        config.getKnobConfigs()[idx].set(config.getKnobConfigs()[knobIndex]);
                }
            } else if (mainPane.getSelectionModel().getSelectedIndex() == 2) {
                var index = slidersTabbedPane.getSelectionModel().getSelectedIndex();
                for (var idx = 0; idx < config.getSliderConfigs().length; idx++) {
                    if (idx != index)
                        config.getSliderConfigs()[idx].set(config.getSliderConfigs()[index]);
                }
            } else if (mainPane.getSelectionModel().getSelectedIndex() == 3) {
                var index = sliderLabelsTabbedPane.getSelectionModel().getSelectedIndex();
                for (var idx = 0; idx < config.getSliderLabelConfigs().length; idx++) {
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
        var config = device.getLightingConfig();
        var mode = config.getLightingMode();
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
            var knobConfigs = config.getKnobConfigs();
            var sliderLabelConfigs = config.getSliderLabelConfigs();
            var sliderConfigs = config.getSliderConfigs();
            var logoConfig = config.getLogoConfig();
            int i;
            for (i = 0; i < NUM_KNOBS; i++) {
                var knobConfig = knobConfigs[i];
                if (knobConfig.getMode() == SINGLE_KNOB_MODE.STATIC) {
                    knobSingleTabPane[i].getSelectionModel().select(0);
                    knobStaticCDs[i].setCustomColor(Color.web(knobConfig.getColor1()));
                } else if (knobConfig.getMode() == SINGLE_KNOB_MODE.VOLUME_GRADIENT) {
                    knobSingleTabPane[i].getSelectionModel().select(1);
                    knobVolumeGradientCD1[i].setCustomColor(Color.web(knobConfig.getColor1()));
                    knobVolumeGradientCD2[i].setCustomColor(Color.web(knobConfig.getColor2()));
                }
            }
            for (i = 0; i < NUM_SLIDERS; i++) {
                var sliderLabelConfig = sliderLabelConfigs[i];
                if (sliderLabelConfig.getMode() == SINGLE_SLIDER_LABEL_MODE.STATIC) {
                    sliderLabelSingleTabPane[i].getSelectionModel().select(0);
                    sliderLabelStaticCDs[i].setCustomColor(Color.web(sliderLabelConfig.getColor()));
                }
            }
            for (i = 0; i < NUM_SLIDERS; i++) {
                var sliderConfig = sliderConfigs[i];
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
        for (var x : xs) {
            for (var cd : x) {
                cd.customColorProperty().addListener((a, bb, c) -> updateColors());
            }
        }
    }

    private void addListener(TabPane[]... xs) {
        for (var x : xs) {
            for (var cd : x) {
                cd.getSelectionModel().selectedItemProperty().addListener((a, bb, c) -> updateColors());
            }
        }
    }

    private void addListener(TabPane... tbs) {
        for (var tb : tbs) {
            tb.getSelectionModel().selectedItemProperty().addListener((a, bb, c) -> updateColors());
        }
    }

    private void initListeners(Slider[] allSliders, CheckBox[] allCheckBoxes) {
        addListener(knobStaticCDs, knobVolumeGradientCD1, knobVolumeGradientCD2,
                sliderStaticCDs, sliderStaticGradientBottomCD, sliderStaticGradientTopCD, sliderVolumeGradientCD1, sliderVolumeGradientCD2,
                sliderLabelStaticCDs);
        addListener(knobSingleTabPane, sliderLabelSingleTabPane, sliderSingleTabPane);
        logoStaticColor.customColorProperty().addListener((a, b, c) -> updateColors());
        allKnobColor.customColorProperty().addListener((observable, oldValue, newValue) -> {
            for (var cd : knobStaticCDs) {
                cd.setCustomColor(newValue);
            }
            updateColors();
        });
        addListener(logoTabPane, fullBodyTabbedPane);
        for (var slider : allSliders) {
            slider.valueProperty().addListener((observable, oldValue, newValue) -> updateColors());
        }
        for (var cb : allCheckBoxes) {
            cb.selectedProperty().addListener((observable, oldValue, newValue) -> updateColors());
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

    @SuppressWarnings("NumericCastThatLosesPrecision")
    private void updateColors() {
        if (mainPane.getSelectionModel().getSelectedIndex() == 0) {
            if (fullBodyTabbedPane.getSelectionModel().getSelectedIndex() == 0) {
                var config = LightingConfig.createAllColor(allKnobColor.getCustomColor());
                device.setLighting(config, false);
            } else if (fullBodyTabbedPane.getSelectionModel().getSelectedIndex() == 1) {
                var config = LightingConfig.createRainbowAnimation((byte) (int) rainbowPhaseShift.getValue(), (byte) (int) rainbowBrightness.getValue(),
                        (byte) (int) rainbowSpeed.getValue(), rainbowReverse.isSelected());
                device.setLighting(config, false);
            } else if (fullBodyTabbedPane.getSelectionModel().getSelectedIndex() == 2) {
                var config = LightingConfig.createWaveAnimation((byte) waveHue.getHue(), (byte) (int) waveBrightness.getValue(), (byte) (int) waveSpeed.getValue(),
                        waveReverse.isSelected(), waveBounce.isSelected());
                device.setLighting(config, false);
            } else if (fullBodyTabbedPane.getSelectionModel().getSelectedIndex() == 3) {
                var config = LightingConfig.createBreathAnimation((byte) breathHue.getHue(), (byte) (int) breathBrightness.getValue(), (byte) (int) breathSpeed.getValue());
                device.setLighting(config, false);
            }
        } else {
            var config = new LightingConfig(NUM_KNOBS, NUM_SLIDERS);
            config.setLightingMode(LightingMode.CUSTOM);
            for (var knob = 0; knob < NUM_KNOBS; knob++) {
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
            for (slider = 0; slider < NUM_SLIDERS; slider++) {
                if (sliderLabelSingleTabPane[slider].getSelectionModel().getSelectedIndex() == 0) {
                    config.getSliderLabelConfigs()[slider].setMode(SINGLE_SLIDER_LABEL_MODE.STATIC);
                    config.getSliderLabelConfigs()[slider].setColor(sliderLabelStaticCDs[slider].getCustomColor());
                }
            }
            for (slider = 0; slider < NUM_SLIDERS; slider++) {
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
        var gp = new GridPane();
        var l1 = new Label(str1);
        var l2 = new Label(str2);
        l1.setWrapText(true);
        l2.setWrapText(true);
        gp.addColumn(0, l1, l2);
        gp.addColumn(1, obj1, obj2);
        return gp;
    }
}
