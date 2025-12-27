package com.getpcpanel.device;

import java.io.IOException;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;

import com.getpcpanel.audio.MicrophoneLevelService;
import com.getpcpanel.commands.IconService;
import com.getpcpanel.hid.DeviceCommunicationHandler;
import com.getpcpanel.hid.InputInterpreter;
import com.getpcpanel.hid.OutputInterpreter;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.LightingConfig;
import com.getpcpanel.profile.LightingConfig.LightingMode;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.SingleKnobLightingConfig.SINGLE_KNOB_MODE;
import com.getpcpanel.profile.SingleLogoLightingConfig;
import com.getpcpanel.profile.SingleLogoLightingConfig.SINGLE_LOGO_MODE;
import com.getpcpanel.profile.SingleSliderLabelLightingConfig.SINGLE_SLIDER_LABEL_MODE;
import com.getpcpanel.profile.SingleSliderLightingConfig.SINGLE_SLIDER_MODE;
import com.getpcpanel.ui.FxHelper;
import com.getpcpanel.ui.HomePage;
import com.getpcpanel.util.Util;
import com.getpcpanel.util.coloroverride.OverrideColorService;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class PCPanelProUI extends Device {
    private final InputInterpreter inputInterpreter;
    private final OverrideColorService overrideColorService;
    private final MicrophoneLevelService microphoneLevelService;

    public static final int KNOB_COUNT = 5;
    public static final int SLIDER_COUNT = 4;
    private static final int LEDS_PER_SLIDER = 5;
    private static final int MAX_ANALOG_VALUE = 100;
    private static final float MIC_MIN_DB = -60.0f;
    private static final float MIC_DEFAULT_MAX_DB = -10.0f;
    @FXML private Pane lightPanes;
    @FXML private Pane panelPane;
    private Label label;
    private Button lightingButton;
    private final Button[] knobs = new Button[9];
    private final ImageView[] images = new ImageView[9];
    private static final Image previewImage = new Image(Objects.requireNonNull(PCPanelProUI.class.getResource("/assets/PCPanelPro/Pro_Cutout.png")).toExternalForm());
    private Stage childDialogStage;
    @FXML private Pane sliderHolder1;
    @FXML private Pane sliderHolder2;
    @FXML private Pane sliderHolder3;
    @FXML private Pane sliderHolder4;
    @FXML private SVGPath sliderLabel1;
    @FXML private SVGPath sliderLabel2;
    @FXML private SVGPath sliderLabel3;
    @FXML private SVGPath sliderLabel4;
    @FXML private SVGPath logoLight;
    @FXML private SVGPath knobColor1;
    @FXML private SVGPath knobColor2;
    @FXML private SVGPath knobColor3;
    @FXML private SVGPath knobColor4;
    @FXML private SVGPath knobColor5;
    @FXML private Pane sliderLightPane1;
    @FXML private Pane sliderLightPane2;
    @FXML private Pane sliderLightPane3;
    @FXML private Pane sliderLightPane4;
    private final Pane[] sliderLightPanes = new Pane[4];
    private final SVGPath[] knobColors = new SVGPath[5];
    private final SVGPath[] sliderLabels = new SVGPath[4];
    private final int[] analogValue = new int[9];
    private final Pane[] sliderHolders = new Pane[4];
    private Timeline micLevelTimeline;

    public PCPanelProUI(FxHelper fxHelper, InputInterpreter inputInterpreter, SaveService saveService, OutputInterpreter outputInterpreter, IconService iconService, ApplicationEventPublisher eventPublisher, OverrideColorService overrideColorService,
            MicrophoneLevelService microphoneLevelService,
            String serialNum, DeviceSave deviceSave) {
        super(fxHelper, saveService, outputInterpreter, iconService, eventPublisher, serialNum, deviceSave);
        this.inputInterpreter = inputInterpreter;
        this.overrideColorService = overrideColorService;
        this.microphoneLevelService = microphoneLevelService;
        var loader = getFxHelper().getLoader(getClass().getResource("/assets/PCPanelPro/PCPanelPro.fxml"));
        loader.setController(this);
        try {
            Pane pane = loader.load();
            Util.fill(sliderLightPanes, (Object[]) new Pane[] { sliderLightPane1, sliderLightPane2, sliderLightPane3, sliderLightPane4 });
            Util.fill(knobColors, (Object[]) new SVGPath[] { knobColor1, knobColor2, knobColor3, knobColor4, knobColor5 });
            Util.fill(sliderLabels, (Object[]) new SVGPath[] { sliderLabel1, sliderLabel2, sliderLabel3, sliderLabel4 });
            Util.fill(sliderHolders, (Object[]) new Pane[] { sliderHolder1, sliderHolder2, sliderHolder3, sliderHolder4 });
            initButtons();
            initLabel();
            initLightingButton();
            pane.getStylesheets().addAll(Objects.requireNonNull(getClass().getResource("/assets/PCPanelPro/PCPanelPro.css")).toExternalForm());
        } catch (IOException e) {
            log.error("Unable to init ui", e);
        }
        postInit();
        initMicLevelTimer();
    }

    @Override
    public Node getLabel() {
        return label;
    }

    @Override
    public Pane getDevicePane() {
        return panelPane;
    }

    private void rotateKnob(int knob, int val) {
        analogValue[knob] = val;
        if (getLightingConfig().getLightingMode() == LightingMode.CUSTOM)
            showLightingConfigToUI(getLightingConfig());
        if (knob < 5) {
            ((Region) knobs[knob].getGraphic()).getChildrenUnmodifiable().get(3).setRotate(Util.analogValueToRotation(val));
        } else {
            var x = Util.map(val, 0.0D, 255.0D, sliderHolders[knob - 5].getPrefHeight(), 0.0D) - 40.0D;
            knobs[knob].setLayoutY(x);
            images[knob].setLayoutY(x);
        }
    }

    @Override
    public int getKnobRotation(int knob) {
        return analogValue[knob];
    }

    private void initLabel() {
        label = new Label("PCPANEL PRO");
        var f = Font.loadFont(getClass().getResourceAsStream("/assets/apex-mk2.regular.otf"), 50.0D);
        label.setFont(f);
        label.setUnderline(true);
        label.setTextFill(Paint.valueOf("white"));
    }

    private void initLightingButton() {
        lightingButton = new Button("Lighting", getLightingImage());
        lightingButton.setStyle("-fx-background-color: transparent;");
        lightingButton.setContentDisplay(ContentDisplay.TOP);
        lightingButton.setMinHeight(100.0D);
        lightingButton.setOnAction(e -> {
            childDialogStage = new Stage();
            getFxHelper().buildProLightingDialog(this).start(childDialogStage);
        });
    }

    private void initMicLevelTimer() {
        micLevelTimeline = new Timeline(new KeyFrame(Duration.millis(50), e -> refreshMicLighting()));
        micLevelTimeline.setCycleCount(Timeline.INDEFINITE);
        micLevelTimeline.play();
    }

    private void refreshMicLighting() {
        var config = getLightingConfig();
        if (config.getLightingMode() != LightingMode.CUSTOM) {
            return;
        }
        if (!hasMicTracking(config)) {
            return;
        }
        setLighting(config, false);
    }

    private boolean hasMicTracking(LightingConfig config) {
        for (var sliderConfig : config.getSliderConfigs()) {
            if (sliderConfig.isMicFollowEnabled()) {
                return true;
            }
        }
        return false;
    }

    private void initButtons() throws IOException {
        var xPos = 121.3D;
        var yPos = 66.3D;
        var xDelta = 133.0D;
        var yDelta = 97.5D;
        var buttonSize = 80;
        for (var i = 0; i < 9; i++) {
            FXMLLoader loader;
            if (i < 5) {
                loader = getFxHelper().getLoader(getClass().getResource("/assets/PCPanelPro/knob.fxml"));
            } else {
                loader = getFxHelper().getLoader(getClass().getResource("/assets/PCPanelPro/slider.fxml"));
            }
            Node nx = loader.load();
            images[i] = buildKnobImageView();
            knobs[i] = new Button("", nx);
            knobs[i].setId("dial_button");
            knobs[i].setContentDisplay(ContentDisplay.CENTER);
            if (i < 5) {
                knobs[i].setMinSize(buttonSize, buttonSize);
                knobs[i].setMaxSize(buttonSize, buttonSize);
                knobs[i].setLayoutX(xPos);
                knobs[i].setLayoutY(yPos);
                knobs[i].setScaleX(1.2D);
                knobs[i].setScaleY(1.2D);

                images[i].setLayoutX(xPos + 5);
                images[i].setLayoutY(yPos + 5);
                images[i].setFitWidth(71);
                images[i].setFitHeight(71);
            } else {
                knobs[i].setMinSize(buttonSize, buttonSize);
                knobs[i].setMaxSize(buttonSize, buttonSize);
                knobs[i].setLayoutX(-26.0D);
                knobs[i].setScaleX(0.4D);
                knobs[i].setScaleY(0.4D);

                images[i].setLayoutX(-11.0D);
                images[i].setFitWidth(50);
                images[i].setFitHeight(50);
            }
            var knob = i;
            knobs[i].setOnAction(e -> {
                HomePage.showHint(false);
                var name = (knob < 5) ? ("Knob " + (knob + 1)) : ("Slider " + (knob - 5 + 1));
                var analogType = (knob < 5) ? "Knob" : "Slider";
                var bm = getFxHelper().buildBasicMacro(this, knob, knob < 5, name, analogType);
                try {
                    childDialogStage = new Stage();
                    bm.start(childDialogStage);
                } catch (Exception ex) {
                    log.error("Unable to start dialog", ex);
                }
            });
            var idx = i;
            knobs[i].setOnMouseClicked(c -> {
                if (c.getButton() == MouseButton.MIDDLE) {
                    try {
                        inputInterpreter.onButtonPress(new DeviceCommunicationHandler.ButtonPressEvent(getSerialNumber(), knob, true));
                    } catch (IOException e1) {
                        log.error("Unable to handle button press", e1);
                    }
                    try {
                        inputInterpreter.onButtonPress(new DeviceCommunicationHandler.ButtonPressEvent(getSerialNumber(), knob, false));
                    } catch (IOException e1) {
                        log.error("Unable to handle button release", e1);
                    }
                } else if (c.getButton() == MouseButton.SECONDARY) {
                    getFxHelper().buildProLightingDialog(this).select(idx).start(new Stage());
                }
            });
            if (i < 5) {
                panelPane.getChildren().add(knobs[i]);
                panelPane.getChildren().add(images[i]);
            } else {
                sliderHolders[i - 5].getChildren().add(knobs[i]);
                sliderHolders[i - 5].getChildren().add(images[i]);
            }
            xPos += xDelta;
            if (i == 1) {
                yPos += yDelta;
                xPos -= 332.5D;
            }
        }
    }

    public String toString() {
        return getDisplayName();
    }

    @Override
    public Image getPreviewImage() {
        return previewImage;
    }

    @Override
    public void setKnobRotation(int knob, int value) {
        Platform.runLater(() -> rotateKnob(knob, value));
    }

    @Override
    public void setButtonPressed(int knob, boolean pressed) {
        Platform.runLater(() -> knobs[knob].setOpacity(pressed ? 0.5D : 1.0D));
    }

    @Override
    public void closeDialogs() {
        if (childDialogStage != null && childDialogStage.isShowing())
            childDialogStage.close();
    }

    @Override
    public Button getLightingButton() {
        return lightingButton;
    }

    @Override
    public DeviceType getDeviceType() {
        return DeviceType.PCPANEL_PRO;
    }

    private void setAllColor(Paint color) {
        for (var p : knobColors) {
            p.setFill(color);
        }
        for (var p : sliderLabels) {
            p.setFill(color);
        }
        for (var pane : sliderLightPanes) {
            for (var n : pane.getChildren())
                ((SVGPath) n).setFill(color);
        }
        logoLight.setFill(color);
    }

    @Override
    public void showLightingConfigToUI(LightingConfig config) {
        var mode = config.getLightingMode();
        if (mode == LightingMode.ALL_COLOR) {
            stopLightingAnimation();
            setAllColor(Paint.valueOf(config.getAllColor()));
        } else if (mode == LightingMode.ALL_RAINBOW) {
            startLightingAnimation("PRO_ALL_RAINBOW", elapsedNs -> applyRainbow(config, elapsedNs));
        } else if (mode == LightingMode.ALL_WAVE) {
            startLightingAnimation("PRO_ALL_WAVE", elapsedNs -> applyWave(config, elapsedNs));
        } else if (mode == LightingMode.ALL_BREATH) {
            startLightingAnimation("PRO_ALL_BREATH", elapsedNs -> applyBreath(config, elapsedNs));
        } else if (mode == LightingMode.CUSTOM) {
            var knobConfigs = config.getKnobConfigs();
            var sliderLabelConfigs = config.getSliderLabelConfigs();
            var sliderConfigs = config.getSliderConfigs();
            for (var i = 0; i < KNOB_COUNT; i++) {
                var knobConfig = overrideColorService.getDialOverride(serialNumber, i).orElse(knobConfigs[i]);
                if (knobConfig.getMode() == SINGLE_KNOB_MODE.STATIC) {
                    knobColors[i].setFill(Paint.valueOf(knobConfig.getColor1()));
                } else if (knobConfig.getMode() == SINGLE_KNOB_MODE.VOLUME_GRADIENT) {
                    var c1 = Color.web(knobConfig.getColor1());
                    var c2 = Color.web(knobConfig.getColor2());
                    knobColors[i].setFill(c1.interpolate(c2, analogValue[i] / (double) MAX_ANALOG_VALUE));
                }
            }
            for (var i = 0; i < SLIDER_COUNT; i++) {
                var sliderLabelConfig = overrideColorService.getSliderLabelOverride(serialNumber, i).orElse(sliderLabelConfigs[i]);
                if (sliderLabelConfig.getMode() == SINGLE_SLIDER_LABEL_MODE.STATIC)
                    sliderLabels[i].setFill(Paint.valueOf(sliderLabelConfig.getColor()));
            }
            for (var i = 0; i < SLIDER_COUNT; i++) {
                var sliderConfig = overrideColorService.getSliderOverride(serialNumber, i).orElse(sliderConfigs[i]);
                if (sliderConfig.getMode() == SINGLE_SLIDER_MODE.STATIC) {
                    for (var n : sliderLightPanes[i].getChildren())
                        ((SVGPath) n).setFill(Paint.valueOf(sliderConfig.getColor1()));
                } else if (sliderConfig.getMode() == SINGLE_SLIDER_MODE.STATIC_GRADIENT) {
                    var c1 = Color.web(sliderConfig.getColor1());
                    var c2 = Color.web(sliderConfig.getColor2());
                    var f = 0.0D;
                    var delta = 0.25D;
                    for (var a = 0; a < LEDS_PER_SLIDER; a++) {
                        ((SVGPath) sliderLightPanes[i].getChildren().get(a)).setFill(c1.interpolate(c2, f));
                        f += delta;
                    }
                } else if (sliderConfig.getMode() == SINGLE_SLIDER_MODE.VOLUME_GRADIENT) {
                    var c1 = Color.web(sliderConfig.getColor1());
                    var c2 = Color.web(sliderConfig.getColor2());
                    if (sliderConfig.isMicFollowEnabled()) {
                        var t = normalizeMicLevel(sliderConfig);
                        var color = c1.interpolate(c2, t);
                        for (var n : sliderLightPanes[i].getChildren()) {
                            ((SVGPath) n).setFill(color);
                        }
                    } else {
                        var f = 0.0D;
                        var delta = 0.25D;
                        var level = analogValue[i + 5];
                        var normalized = Math.max(0.0D, Math.min(1.0D, level / 255.0D));
                        var lit = (int) Math.round(normalized * LEDS_PER_SLIDER);
                        if (lit < 0) {
                            lit = 0;
                        } else if (lit > LEDS_PER_SLIDER) {
                            lit = LEDS_PER_SLIDER;
                        }
                        for (var a = 0; a < 5; a++) {
                            if (a < lit) {
                                ((SVGPath) sliderLightPanes[i].getChildren().get(a)).setFill(c1.interpolate(c2, f));
                            } else {
                                ((SVGPath) sliderLightPanes[i].getChildren().get(a)).setFill(Paint.valueOf("black"));
                            }
                            f += delta;
                        }
                    }
                }
            }

            var logoConfig = overrideColorService.getLogoOverride(serialNumber).orElse(config.getLogoConfig());
            if (logoConfig.getMode() == SINGLE_LOGO_MODE.STATIC) {
                stopLightingAnimation();
                logoLight.setFill(Paint.valueOf(logoConfig.getColor()));
            } else if (logoConfig.getMode() == SINGLE_LOGO_MODE.RAINBOW) {
                startLightingAnimation("PRO_CUSTOM_LOGO_RAINBOW", elapsedNs -> applyLogoRainbow(logoConfig, elapsedNs));
            } else if (logoConfig.getMode() == SINGLE_LOGO_MODE.BREATH) {
                startLightingAnimation("PRO_CUSTOM_LOGO_BREATH", elapsedNs -> applyLogoBreath(logoConfig, elapsedNs));
            } else {
                stopLightingAnimation();
            }
        }
    }

    private void applyRainbow(LightingConfig config, long elapsedNs) {
        var elapsedSeconds = elapsedNs / 1_000_000_000.0D;
        var hueShift = timeToHueShift(elapsedSeconds, config.getRainbowSpeed(), config.getRainbowReverse() == 1);
        var totalRows = 9;
        var row = 0;
        knobColor1.setFill(createFill(config, totalRows, row, hueShift));
        knobColor2.setFill(createFill(config, totalRows, row, hueShift));
        row++;
        knobColor3.setFill(createFill(config, totalRows, row, hueShift));
        knobColor4.setFill(createFill(config, totalRows, row, hueShift));
        knobColor5.setFill(createFill(config, totalRows, row, hueShift));
        row++;
        for (var p : sliderLabels) {
            p.setFill(createFill(config, totalRows, row, hueShift));
        }
        row++;
        for (var i = 4; i >= 0; i--) {
            for (var a = 0; a < 4; a++) {
                ((SVGPath) sliderLightPanes[a].getChildren().get(i)).setFill(createFill(config, totalRows, row, hueShift));
            }
            row++;
        }
        logoLight.setFill(createFill(config, totalRows, row, hueShift));
    }

    private void applyWave(LightingConfig config, long elapsedNs) {
        var elapsedSeconds = elapsedNs / 1_000_000_000.0D;
        var hue = hueFromByte(config.getWaveHue());
        var baseBrightness = byteToUnit(config.getWaveBrightness());
        var phase = wavePhase(elapsedSeconds, config.getWaveSpeed(), config.getWaveReverse() == 1, config.getWaveBounce() == 1);
        var totalRows = 9;
        var row = 0;
        var color = Color.hsb(hue, 1.0D, baseBrightness * waveRowBrightness(totalRows, row, phase));
        knobColor1.setFill(color);
        knobColor2.setFill(color);
        row++;
        color = Color.hsb(hue, 1.0D, baseBrightness * waveRowBrightness(totalRows, row, phase));
        knobColor3.setFill(color);
        knobColor4.setFill(color);
        knobColor5.setFill(color);
        row++;
        color = Color.hsb(hue, 1.0D, baseBrightness * waveRowBrightness(totalRows, row, phase));
        for (var p : sliderLabels) {
            p.setFill(color);
        }
        row++;
        for (var i = 4; i >= 0; i--) {
            color = Color.hsb(hue, 1.0D, baseBrightness * waveRowBrightness(totalRows, row, phase));
            for (var a = 0; a < 4; a++) {
                ((SVGPath) sliderLightPanes[a].getChildren().get(i)).setFill(color);
            }
            row++;
        }
        logoLight.setFill(Color.hsb(hue, 1.0D, baseBrightness * waveRowBrightness(totalRows, row, phase)));
    }

    private void applyBreath(LightingConfig config, long elapsedNs) {
        var elapsedSeconds = elapsedNs / 1_000_000_000.0D;
        var hue = hueFromByte(config.getBreathHue());
        var baseBrightness = byteToUnit(config.getBreathBrightness());
        var factor = breathFactor(elapsedSeconds, config.getBreathSpeed());
        setAllColor(Color.hsb(hue, 1.0D, baseBrightness * factor));
    }

    private void applyLogoRainbow(SingleLogoLightingConfig logoConfig, long elapsedNs) {
        var elapsedSeconds = elapsedNs / 1_000_000_000.0D;
        var hueShift = timeToHueShift(elapsedSeconds, logoConfig.getSpeed(), false);
        var brightness = byteToUnit(logoConfig.getBrightness());
        logoLight.setFill(Color.hsb(normalizeHue(hueShift), 1.0D, brightness));
    }

    private void applyLogoBreath(SingleLogoLightingConfig logoConfig, long elapsedNs) {
        var elapsedSeconds = elapsedNs / 1_000_000_000.0D;
        var hue = hueFromByte(logoConfig.getHue());
        var baseBrightness = byteToUnit(logoConfig.getBrightness());
        var factor = breathFactor(elapsedSeconds, logoConfig.getSpeed());
        logoLight.setFill(Color.hsb(hue, 1.0D, baseBrightness * factor));
    }

    private static double waveRowBrightness(int totalRows, int row, double phase) {
        var offset = 2.0D * Math.PI * (totalRows - row - 1) / totalRows;
        return 0.5D + 0.5D * Math.sin(phase + offset);
    }

    private static Color createFill(LightingConfig config, int totalRows, int row, double hueShift) {
        var baseHue = (360 * (totalRows - row - 1) * (0xFF & config.getRainbowPhaseShift())) / 255.0D * totalRows;
        var hue = normalizeHue(baseHue + hueShift);
        var brightness = byteToUnit(config.getRainbowBrightness());
        return Color.hsb(hue, 1.0D, brightness);
    }

    private float normalizeMicLevel(com.getpcpanel.profile.SingleSliderLightingConfig cfg) {
        var maxDb = cfg.getMicMaxDb() == null ? MIC_DEFAULT_MAX_DB : cfg.getMicMaxDb();
        var db = microphoneLevelService.getLevelDb(cfg.getMicDeviceId());
        if (Float.isNaN(db) || db <= MIC_MIN_DB) {
            return 0.0f;
        }
        if (db >= maxDb) {
            return 1.0f;
        }
        return (db - MIC_MIN_DB) / (maxDb - MIC_MIN_DB);
    }

    @Override
    protected ImageView[] getKnobImages() {
        return images;
    }
}
