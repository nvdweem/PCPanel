package com.getpcpanel.device;

import java.io.IOException;
import java.util.Objects;

import com.getpcpanel.Main;
import com.getpcpanel.hid.InputInterpreter;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.LightingConfig;
import com.getpcpanel.profile.LightingConfig.LightingMode;
import com.getpcpanel.profile.SingleKnobLightingConfig.SINGLE_KNOB_MODE;
import com.getpcpanel.profile.SingleLogoLightingConfig.SINGLE_LOGO_MODE;
import com.getpcpanel.profile.SingleSliderLabelLightingConfig.SINGLE_SLIDER_LABEL_MODE;
import com.getpcpanel.profile.SingleSliderLightingConfig.SINGLE_SLIDER_MODE;
import com.getpcpanel.ui.BasicMacro;
import com.getpcpanel.ui.ProLightingDialog;
import com.getpcpanel.util.Util;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class PCPanelProUI extends Device {
    public static final int KNOB_COUNT = 5;
    public static final int SLIDER_COUNT = 4;
    private static final int LEDS_PER_SLIDER = 5;
    private static final int MAX_ANALOG_VALUE = 100;
    @FXML private Pane lightPanes;
    @FXML private Pane panelPane;
    private Label label;
    private Button lightingButton;
    private final Button[] knobs = new Button[9];
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

    public PCPanelProUI(String serialNum, DeviceSave deviceSave) {
        super(serialNum, deviceSave);
        var loader = new FXMLLoader(getClass().getResource("/assets/PCPanelPro/PCPanelPro.fxml"));
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
            var x = Util.map(val, 0.0D, 100.0D, sliderHolders[knob - 5].getPrefHeight(), 0.0D) - 40.0D;
            knobs[knob].setLayoutY(x);
        }
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
            new ProLightingDialog(this).start(childDialogStage);
        });
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
                loader = new FXMLLoader(getClass().getResource("/assets/PCPanelPro/knob.fxml"));
            } else {
                loader = new FXMLLoader(getClass().getResource("/assets/PCPanelPro/slider.fxml"));
            }
            Node nx = loader.load();
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
            } else {
                knobs[i].setMinSize(buttonSize, buttonSize);
                knobs[i].setMaxSize(buttonSize, buttonSize);
                knobs[i].setLayoutX(-26.0D);
                knobs[i].setScaleX(0.4D);
                knobs[i].setScaleY(0.4D);
            }
            var knob = i;
            knobs[i].setOnAction(e -> {
                Main.showHint(false);
                var name = (knob < 5) ? ("Knob " + (knob + 1)) : ("Slider " + (knob - 5 + 1));
                var analogType = (knob < 5) ? "Knob" : "Slider";
                var bm = new BasicMacro(this, knob, knob < 5, name, analogType);
                try {
                    childDialogStage = new Stage();
                    bm.start(childDialogStage);
                } catch (Exception ex) {
                    log.error("Unable to start dialog", ex);
                }
            });
            knobs[i].setOnMouseClicked(c -> {
                if (c.getButton() == MouseButton.MIDDLE) {
                    try {
                        InputInterpreter.onButtonPress(getSerialNumber(), knob, true);
                    } catch (IOException e1) {
                        log.error("Unable to handle button press", e1);
                    }
                    try {
                        InputInterpreter.onButtonPress(getSerialNumber(), knob, false);
                    } catch (IOException e1) {
                        log.error("Unable to handle button release", e1);
                    }
                }
            });
            if (i < 5) {
                panelPane.getChildren().add(knobs[i]);
            } else {
                sliderHolders[i - 5].getChildren().add(knobs[i]);
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
            setAllColor(Paint.valueOf(config.getAllColor()));
        } else if (mode == LightingMode.ALL_RAINBOW) {
            var totalRows = 9;
            var row = 0;
            knobColor1.setFill(createFill(config, totalRows, row));
            knobColor2.setFill(createFill(config, totalRows, row));
            row++;
            knobColor3.setFill(createFill(config, totalRows, row));
            knobColor4.setFill(createFill(config, totalRows, row));
            knobColor5.setFill(createFill(config, totalRows, row));
            row++;
            for (var p : sliderLabels) {
                p.setFill(createFill(config, totalRows, row));
            }
            row++;
            for (var i = 4; i >= 0; i--) {
                for (var a = 0; a < 4; a++)
                    ((SVGPath) sliderLightPanes[a].getChildren().get(i)).setFill(createFill(config, totalRows, row));
                row++;
            }
            logoLight.setFill(createFill(config, totalRows, row));
        } else if (mode == LightingMode.ALL_WAVE) {
            setAllColor(Color.hsb(360.0D * (0xFF & config.getWaveHue()) / 255.0D, 1.0D, (0xFF & config.getWaveBrightness()) / 255.0D));
        } else if (mode == LightingMode.ALL_BREATH) {
            setAllColor(Color.hsb(360.0D * (0xFF & config.getBreathHue()) / 255.0D, 1.0D, (0xFF & config.getBreathBrightness()) / 255.0D));
        } else if (mode == LightingMode.CUSTOM) {
            var knobConfigs = config.getKnobConfigs();
            var sliderLabelConfigs = config.getSliderLabelConfigs();
            var sliderConfigs = config.getSliderConfigs();
            var logoConfig = config.getLogoConfig();
            for (var i = 0; i < KNOB_COUNT; i++) {
                var knobConfig = knobConfigs[i];
                if (knobConfig.getMode() == SINGLE_KNOB_MODE.STATIC) {
                    knobColors[i].setFill(Paint.valueOf(knobConfig.getColor1()));
                } else if (knobConfig.getMode() == SINGLE_KNOB_MODE.VOLUME_GRADIENT) {
                    var c1 = Color.web(knobConfig.getColor1());
                    var c2 = Color.web(knobConfig.getColor2());
                    knobColors[i].setFill(c1.interpolate(c2, analogValue[i] / 100.0D));
                }
            }
            for (var i = 0; i < SLIDER_COUNT; i++) {
                var sliderLabelConfig = sliderLabelConfigs[i];
                if (sliderLabelConfig.getMode() == SINGLE_SLIDER_LABEL_MODE.STATIC)
                    sliderLabels[i].setFill(Paint.valueOf(sliderLabelConfig.getColor()));
            }
            for (var i = 0; i < SLIDER_COUNT; i++) {
                var sliderConfig = sliderConfigs[i];
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
                    var f = 0.0D;
                    var delta = 0.25D;
                    for (var a = 0; a < 5; a++) {
                        if (a < (analogValue[i + 5] + 10) * 5 / MAX_ANALOG_VALUE) {
                            ((SVGPath) sliderLightPanes[i].getChildren().get(a)).setFill(c1.interpolate(c2, f));
                        } else {
                            ((SVGPath) sliderLightPanes[i].getChildren().get(a)).setFill(Paint.valueOf("black"));
                        }
                        f += delta;
                    }
                }
            }
            if (logoConfig.getMode() == SINGLE_LOGO_MODE.STATIC) {
                logoLight.setFill(Paint.valueOf(logoConfig.getColor()));
            } else if (logoConfig.getMode() == SINGLE_LOGO_MODE.RAINBOW) {
                logoLight.setFill(Color.RED);
            } else if (logoConfig.getMode() == SINGLE_LOGO_MODE.BREATH) {
                logoLight.setFill(Color.hsb(360.0D * (0xFF & logoConfig.getHue()) / 255.0D, 1.0D, (0xFF & logoConfig.getBrightness()) / 255.0D));
            }
        }
    }

    private static Color createFill(LightingConfig config, int totalRows, int row) {
        return Color.hsb((360 * (totalRows - row - 1) * (0xFF & config.getRainbowPhaseShift())) / 255.0D * totalRows, 1.0D, (0xFF & config.getRainbowBrightness()) / 255.0D);
    }
}
