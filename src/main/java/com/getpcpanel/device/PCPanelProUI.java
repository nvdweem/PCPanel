package com.getpcpanel.device;

import java.io.IOException;

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
import com.sun.javafx.webkit.Accessor;

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
import javafx.scene.web.WebView;
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
    @FXML private WebView webview;
    private Label label;
    private Button lightingButton;
    private final Button[] knobs = new Button[9];
    private static final Image previewImage = new Image(PCPanelProUI.class.getResourceAsStream("/assets/PCPanelPro/Pro_Cutout.png"));
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
            initBox();
            initButtons();
            initLabel();
            initLightingButton();
            pane.getStylesheets().addAll(getClass().getResource("/assets/PCPanelPro/PCPanelPro.css").toExternalForm());
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

    private void initBox() {
        webview.setPrefSize(480.0D, 610.0D);
        webview.setMaxSize(480.0D, 610.0D);
        webview.getEngine()
               .loadContent(
                       "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" viewBox=\"0 0 376.56 482.21\"><defs><style>.cls-1{fill:#acb4c1;}.cls-2{fill:url(#linear-gradient);}.cls-3{fill:#212121;}.cls-4{fill:#7f7f7f;}</style><linearGradient id=\"linear-gradient\" x1=\"359.26\" y1=\"18.62\" x2=\"359.26\" y2=\"487.26\" gradientUnits=\"userSpaceOnUse\"><stop offset=\"0\" stop-color=\"#5e6268\" stop-opacity=\"0.5\"/><stop offset=\"1\" stop-opacity=\"0\"/></linearGradient></defs><g id=\"Layer_2\" data-name=\"Layer 2\"><g id=\"Layer_1-2\" data-name=\"Layer 1\"><path class=\"cls-1\" d=\"M346.27,0H50.32A30.13,30.13,0,0,0,32.85,5.57c-.24.15-.48.29-.72.45L13.66,18.18A30.27,30.27,0,0,0,0,43.48V451.93a30.31,30.31,0,0,0,30.28,30.28h296a30.13,30.13,0,0,0,17-5.24l18.47-12.17.32-.22a30.3,30.3,0,0,0,14.52-25.85V30.28A30.32,30.32,0,0,0,346.27,0Z\"/><path class=\"cls-2\" d=\"M375.38,31.06V439.51a30.27,30.27,0,0,1-13.66,25.29l-.4.26-.39.26-.39.26-.4.26-.39.26-.39.26-.4.25-.39.26-.39.26-.39.26-.4.26-.39.26-.39.26-.4.26-.39.26-.39.25-.39.26-.4.26-.39.26-.39.26-.4.26-.39.26-.39.26-.4.25-.39.26-.39.26-.4.26-.39.26-.39.26-.39.26-.4.26-.39.26-.39.25c-.13.09-.26.18-.4.26l-.39.26-.39.26-.39.26-.4.26-.39.26-.39.26-.4.26-.39.25-.39.26-.39.26-.4.26-.39.26-.39.26-.1.06a30.25,30.25,0,0,0,13.37-25.1V43.48a29.73,29.73,0,0,0-.67-6.3l19.38-9A27.73,27.73,0,0,1,375.38,31.06Z\"/><path class=\"cls-3\" d=\"M353.68,43.48V451.93a27.45,27.45,0,0,1-27.45,27.45h-296A27.45,27.45,0,0,1,2.83,451.93V43.48A27.45,27.45,0,0,1,30.28,16h296a27.43,27.43,0,0,1,27,22.38A26.49,26.49,0,0,1,353.68,43.48Z\"/><rect class=\"cls-4\" x=\"63.19\" y=\"248.28\" width=\"7.72\" height=\"203.87\" rx=\"1.42\"/><rect class=\"cls-4\" x=\"137.33\" y=\"248.28\" width=\"7.72\" height=\"203.87\" rx=\"0.71\"/><rect class=\"cls-4\" x=\"211.46\" y=\"248.28\" width=\"7.72\" height=\"203.87\" rx=\"1.42\"/><rect class=\"cls-4\" x=\"285.6\" y=\"248.28\" width=\"7.72\" height=\"203.87\" rx=\"1.42\"/><path class=\"cls-3\" d=\"M199.6,6H176.85c-.68,0-.77-.39-.2-.87h0a4.19,4.19,0,0,1,2.28-.87h22.75c.68,0,.77.39.2.87h0A4.19,4.19,0,0,1,199.6,6Z\"/></g></g></svg>");
        Accessor.getPageFor(webview.getEngine()).setBackgroundColor(0);
    }

    private void initLightingButton() {
        var webview = new WebView();
        webview.setPrefSize(50.0D, 50.0D);
        webview.setMaxSize(50.0D, 50.0D);
        webview.setZoom(0.8D);
        webview.getEngine()
               .loadContent(
                       "<svg version=\"1.1\" id=\"Layer_1\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" x=\"0px\" y=\"0px\"\t viewBox=\"0 0 1000 1000\" style=\"enable-background:new 0 0 1000 1000;\" xml:space=\"preserve\"><style type=\"text/css\">\t.st0{fill:#FFFFFF;}</style><g>\t<g transform=\"translate(0.000000,512.000000) scale(0.100000,-0.100000)\">\t\t<path class=\"st0\" d=\"M4879.2,4997.3c-78.6-34.5-159.1-132.2-172.5-208.9c-5.7-34.5-9.6-364.1-5.7-735.9\t\t\tc5.7-645.8,7.7-676.5,46-726.3c74.7-101.6,136.1-132.2,256.8-132.2c120.7,0,182,30.7,256.8,132.2c38.3,51.7,40.2,74.7,40.2,778\t\t\ts-1.9,726.3-40.2,778c-21.1,28.7-61.3,70.9-88.2,90.1C5109.1,5020.3,4955.8,5033.7,4879.2,4997.3z\"/>\t\t<path class=\"st0\" d=\"M1655.9,3661.6c-84.3-24.9-172.5-118.8-199.3-214.6c-42.2-159.1-24.9-184,515.5-726.3\t\t\tc408.2-408.2,505.9-494.4,569.2-511.7c218.5-59.4,431.2,153.3,371.8,371.8c-17.2,63.2-105.4,161-492.5,553.8\t\t\tC1876.2,3682.6,1832.2,3713.3,1655.9,3661.6z\"/>\t\t<path class=\"st0\" d=\"M8137,3646.2c-47.9-23-253-214.6-550-511.7c-392.9-398.6-475.3-490.6-492.5-553.8\t\t\tc-57.5-218.5,153.3-431.2,371.8-371.8c63.2,17.2,161,105.4,553.8,492.5c509.7,507.8,563.4,576.8,538.5,711\t\t\tC8522.1,3615.6,8313.3,3732.5,8137,3646.2z\"/>\t\t<path class=\"st0\" d=\"M4735.4,2559.7c-398.6-51.7-780-193.5-1111.5-415.8c-184-122.7-523.2-461.8-645.8-645.8\t\t\tC2796,1226,2665.7,919.4,2596.7,597.4c-49.8-228-49.8-730.1,0-958.2c107.3-496.3,320-887.3,680.3-1247.5\t\t\tc360.3-360.3,751.2-573,1247.5-680.3c228-49.8,730.1-49.8,958.2,0c492.5,105.4,893,323.9,1247.5,680.3\t\t\tc356.5,354.5,574.9,755,680.3,1247.5c49.8,228,49.8,730.1,0,958.2c-107.3,496.3-320,887.3-680.3,1247.5\t\t\tc-354.5,354.5-726.3,561.5-1209.2,670.7C5362.1,2552,4886.8,2578.8,4735.4,2559.7z M5446.4,1902.4\t\t\tc348.8-90.1,613.2-245.3,870-507.8C6868.3,829.2,7000.6,5.2,6647.9-694.3c-107.3-216.5-172.5-306.6-348.8-482.9\t\t\ts-266.4-241.5-482.9-348.8c-293.2-147.6-590.2-208.9-916-189.7c-469.5,28.8-845.1,197.4-1180.5,530.8\t\t\tc-266.4,262.5-421.6,536.6-511.7,893c-46,182.1-46,638.1,0,820.2c86.2,343,245.3,626.7,492.5,875.8\t\t\tc281.7,287.5,592.1,452.3,983.1,527C4875.3,1967.5,5256.7,1954.1,5446.4,1902.4z\"/>\t\t<path class=\"st0\" d=\"M279.9,398C151.5,342.5,78.7,194.9,105.5,51.2c17.2-90.1,113.1-195.5,199.3-220.4\t\t\tc46-13.4,306.6-19.2,755-15.3c655.4,5.8,686,7.7,735.9,46C1897.3-63.8,1928-2.5,1928,118.2s-30.7,182.1-132.2,256.8\t\t\tc-49.8,38.3-78.6,40.2-755,44.1C485,422.9,325.9,419.1,279.9,398z\"/>\t\t<path class=\"st0\" d=\"M8251.9,398c-128.4-55.6-201.2-203.1-174.4-346.8c17.2-90.1,113.1-195.5,199.3-220.4\t\t\tc46-13.4,306.6-19.2,755-15.3c655.4,5.8,686.1,7.7,735.9,46C9869.3-63.8,9900-2.5,9900,118.2s-30.7,182.1-132.2,256.8\t\t\tc-49.8,38.3-78.6,40.2-755,44.1C8457,422.9,8297.9,419.1,8251.9,398z\"/>\t\t<path class=\"st0\" d=\"M2522.1-1980.2c-57.5-21.1-193.6-145.6-557.7-511.7c-394.8-396.7-486.8-498.3-505.9-561.5\t\t\tc-65.2-222.3,151.4-438.8,373.7-373.7c63.2,19.2,164.8,111.2,569.2,513.6c408.2,408.2,494.4,505.9,511.7,569.2\t\t\tC2974.3-2114.3,2746.3-1899.7,2522.1-1980.2z\"/>\t\t<path class=\"st0\" d=\"M7293.8-1980.2c-151.4-53.7-239.5-214.6-199.3-364.1c17.3-63.2,105.4-161,492.5-553.8\t\t\tc513.6-515.5,574.9-561.5,716.7-538.5c103.5,19.2,218.5,122.7,247.2,226.1c42.2,157.1,21.1,187.8-496.3,709.1\t\t\tc-260.6,262.5-494.4,486.8-521.2,502.1C7452.8-1957.2,7374.2-1951.4,7293.8-1980.2z\"/>\t\t<path class=\"st0\" d=\"M4879.2-2974.8c-78.6-34.5-159.1-132.2-172.5-208.9c-5.7-34.5-9.6-364.1-5.7-735.9\t\t\tc5.7-645.8,7.7-676.5,46-726.3c74.7-101.6,136.1-132.2,256.8-132.2c120.7,0,182,30.7,256.8,132.2c38.3,51.7,40.2,74.7,40.2,778\t\t\ts-1.9,726.3-40.2,778c-21.1,28.7-61.3,70.9-88.2,90.1C5109.1-2951.8,4955.8-2938.3,4879.2-2974.8z\"/>/g></g></svg>");
        webview.setMouseTransparent(true);
        Accessor.getPageFor(webview.getEngine()).setBackgroundColor(0);
        lightingButton = new Button("Lighting", webview);
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
