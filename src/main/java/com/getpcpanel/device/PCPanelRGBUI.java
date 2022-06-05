package com.getpcpanel.device;

import java.io.IOException;
import java.util.Objects;

import com.getpcpanel.hid.DeviceCommunicationHandler;
import com.getpcpanel.hid.InputInterpreter;
import com.getpcpanel.hid.OutputInterpreter;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.LightingConfig;
import com.getpcpanel.profile.LightingConfig.LightingMode;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.ui.FxHelper;
import com.getpcpanel.ui.HomePage;
import com.getpcpanel.util.Util;

import javafx.application.Platform;
import javafx.fxml.FXML;
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
import javafx.scene.shape.Shape;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class PCPanelRGBUI extends Device {
    private final InputInterpreter inputInterpreter;

    private static final int KNOB_COUNT = 4;
    @FXML private Pane lightPanes;
    @FXML private Pane panelPane;
    private Label label;
    private Button lightingButton;
    private final Button[] knobs = new Button[KNOB_COUNT];
    private static final Image previewImage = new Image(Objects.requireNonNull(PCPanelRGBUI.class.getResource("/assets/PCPanelRGB/preview.png")).toExternalForm());
    private Stage childDialogStage;

    public PCPanelRGBUI(FxHelper fxHelper, InputInterpreter inputInterpreter, SaveService saveService, OutputInterpreter outputInterpreter, DeviceSave deviceSave, String serialNum) {
        super(fxHelper, saveService, outputInterpreter, serialNum, deviceSave);
        this.inputInterpreter = inputInterpreter;
        var loader = getFxHelper().getLoader(getClass().getResource("/assets/PCPanelRGB/PCPanelRGB.fxml"));
        loader.setController(this);
        try {
            Pane pane = loader.load();
            initButtons();
            initLabel();
            initLightingButton();
            pane.getStylesheets().addAll(Objects.requireNonNull(getClass().getResource("/assets/PCPanelRGB/PCPanelRGB.css")).toExternalForm());
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
        ((Region) knobs[knob].getGraphic()).getChildrenUnmodifiable().get(3).setRotate(Util.analogValueToRotation(val));
    }

    @Override
    public int getKnobRotation(int knob) {
        //noinspection NumericCastThatLosesPrecision
        return Util.rotationToAnalogValue((int) ((Region) knobs[knob].getGraphic()).getChildrenUnmodifiable().get(3).getRotate());
    }

    private void initLabel() {
        label = new Label("PCPANEL RGB");
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
            getFxHelper().buildRGBLightingDialog(this).start(childDialogStage);
        });
    }

    private void initButtons() throws IOException {
        var xPos = 52.0D;
        var yPos = 64.0D;
        var xDelta = 107.3D;
        var buttonSize = 80;
        for (var i = 0; i < KNOB_COUNT; i++) {
            var loader = getFxHelper().getLoader(getClass().getResource("/assets/PCPanelRGB/knob.fxml"));
            Node nx = loader.load();
            knobs[i] = new Button("", nx);
            knobs[i].setId("dial_button");
            knobs[i].setContentDisplay(ContentDisplay.CENTER);
            knobs[i].setMinSize(buttonSize, buttonSize);
            knobs[i].setMaxSize(buttonSize, buttonSize);
            knobs[i].setLayoutX(xPos);
            knobs[i].setLayoutY(yPos);
            var knob = i;
            knobs[i].setOnAction(e -> {
                HomePage.showHint(false);
                var bm = getFxHelper().buildBasicMacro(this, knob);
                try {
                    childDialogStage = new Stage();
                    bm.start(childDialogStage);
                } catch (Exception ex) {
                    log.error("Unable to init button", ex);
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
                    getFxHelper().buildRGBLightingDialog(this).select(idx).start(new Stage());
                }
            });
            panelPane.getChildren().add(knobs[i]);
            xPos += xDelta;
        }
    }

    public String toString() {
        return getDisplayName();
    }

    @Override
    public Image getPreviewImage() {
        return previewImage;
    }

    public int getKnobCount() {
        return KNOB_COUNT;
    }

    @Override
    public void setKnobRotation(int knob, int value) {
        Platform.runLater(() -> rotateKnob(knob, value));
    }

    @Override
    public void setButtonPressed(int knob, boolean pressed) {
        Platform.runLater(() -> knobs[knob].setOpacity(pressed ? 0.5D : 1.0D));
    }

    private void setAllKnobUIColor(Color color) {
        for (var i = 0; i < getKnobCount(); i++) {
            setKnobUIColor(i, color);
        }
    }

    private void setKnobUIColorHex(int knob, String color) {
        var lightPane = (Shape) lightPanes.getChildren().get(knob);
        lightPane.setFill(Paint.valueOf(color));
    }

    private void setKnobUIColor(int knob, Color color) {
        var lightPane = (Shape) lightPanes.getChildren().get(knob);
        lightPane.setFill(color);
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
        return DeviceType.PCPANEL_RGB;
    }

    @Override
    public void showLightingConfigToUI(LightingConfig config) {
        var mode = config.getLightingMode();
        if (mode == LightingMode.ALL_COLOR) {
            setAllKnobUIColor(Color.valueOf(config.getAllColor()));
        } else if (mode == LightingMode.SINGLE_COLOR) {
            for (var i = 0; i < getKnobCount(); i++)
                setKnobUIColorHex(i, config.getIndividualColors()[i]);
        } else if (mode == LightingMode.ALL_RAINBOW) {
            for (var i = 0; i < getKnobCount(); i++)
                setKnobUIColor(i,
                        Color.hsb((360 * (getKnobCount() - i - 1) * (0xFF & config.getRainbowPhaseShift())) / 255.0D * getKnobCount(), 1.0D, (0xFF & config.getRainbowBrightness()) / 255.0D));
        } else if (mode == LightingMode.ALL_WAVE) {
            for (var i = 0; i < getKnobCount(); i++)
                setKnobUIColor(i, Color.hsb(360.0D * (0xFF & config.getWaveHue()) / 255.0D, 1.0D, (0xFF & config.getWaveBrightness()) / 255.0D));
        } else if (mode == LightingMode.ALL_BREATH) {
            for (var i = 0; i < getKnobCount(); i++)
                setKnobUIColor(i, Color.hsb(360.0D * (0xFF & config.getBreathHue()) / 255.0D, 1.0D, (0xFF & config.getBreathBrightness()) / 255.0D));
        }
    }
}
