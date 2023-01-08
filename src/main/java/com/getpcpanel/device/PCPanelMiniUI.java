package com.getpcpanel.device;

import java.io.IOException;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;

import com.getpcpanel.commands.IconService;
import com.getpcpanel.hid.DeviceCommunicationHandler;
import com.getpcpanel.hid.InputInterpreter;
import com.getpcpanel.hid.OutputInterpreter;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.LightingConfig;
import com.getpcpanel.profile.LightingConfig.LightingMode;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.SingleKnobLightingConfig.SINGLE_KNOB_MODE;
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
import javafx.scene.image.ImageView;
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
public class PCPanelMiniUI extends Device {
    private final InputInterpreter inputInterpreter;

    public static final int KNOB_COUNT = 4;
    private static final double MAX_ANALOG_VALUE = 100;
    @FXML private Pane lightPanes;
    @FXML private Pane panelPane;
    private Label label;
    private Button lightingButton;
    private final Button[] knobs = new Button[KNOB_COUNT];
    private final int[] analogValue = new int[KNOB_COUNT];
    private final ImageView[] images = new ImageView[KNOB_COUNT];
    private static final Image previewImage = new Image(Objects.requireNonNull(PCPanelMiniUI.class.getResource("/assets/PCPanelMini/preview.png")).toExternalForm());
    private Stage childDialogStage;

    public PCPanelMiniUI(FxHelper fxHelper, InputInterpreter inputInterpreter, SaveService saveService, OutputInterpreter outputInterpreter, IconService iconService, ApplicationEventPublisher eventPublisher, String serialNum, DeviceSave deviceSave) {
        super(fxHelper, saveService, outputInterpreter, iconService, eventPublisher, serialNum, deviceSave);
        this.inputInterpreter = inputInterpreter;
        var loader = getFxHelper().getLoader(getClass().getResource("/assets/PCPanelMini/PCPanelMini.fxml"));
        loader.setController(this);
        try {
            Pane pane = loader.load();
            initButtons();
            initLabel();
            initLightingButton();
            pane.getStylesheets().addAll(Objects.requireNonNull(getClass().getResource("/assets/PCPanelMini/PCPanelMini.css")).toExternalForm());
        } catch (IOException e) {
            log.error("Unable to initialize ui", e);
        }
        postInit();
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
        if (knob >= analogValue.length) {
            log.error("Getting knob {} value ({}), but the amount of knobs is less: {}", knob, val, analogValue.length);
            return;
        }
        analogValue[knob] = val;
        if (getLightingConfig().getLightingMode() == LightingMode.CUSTOM)
            showLightingConfigToUI(getLightingConfig());
        ((Region) knobs[knob].getGraphic()).getChildrenUnmodifiable().get(3).setRotate(Util.analogValueToRotation(val));
    }

    @Override
    public int getKnobRotation(int knob) {
        return analogValue[knob];
    }

    private void initLabel() {
        label = new Label("PCPANEL MINI");
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
            getFxHelper().buildMiniLightingDialog(this).start(childDialogStage);
        });
    }

    private void initButtons() throws IOException {
        var xPos = 56.3D;
        var yPos = 133.4D;
        var xDelta = 115.0D;
        var buttonSize = 80;
        for (var i = 0; i < KNOB_COUNT; i++) {
            var loader = getFxHelper().getLoader(getClass().getResource("/assets/PCPanelMini/knob.fxml"));
            Node nx = loader.load();
            images[i] = buildKnobImageView();
            knobs[i] = new Button("", nx);
            knobs[i].setId("dial_button");
            knobs[i].setContentDisplay(ContentDisplay.CENTER);
            knobs[i].setMinSize(buttonSize, buttonSize);
            knobs[i].setMaxSize(buttonSize, buttonSize);
            knobs[i].setLayoutX(xPos);
            knobs[i].setLayoutY(yPos);
            knobs[i].setScaleX(1.2D);
            knobs[i].setScaleY(1.2D);

            images[i].setLayoutX(xPos + 5);
            images[i].setLayoutY(yPos + 5);
            images[i].setFitWidth(70);
            images[i].setFitHeight(70);

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
                        log.error("Unable to handle button up", e1);
                    }
                } else if (c.getButton() == MouseButton.SECONDARY) {
                    getFxHelper().buildMiniLightingDialog(this).select(idx).start(new Stage());
                }
            });
            panelPane.getChildren().add(knobs[i]);
            panelPane.getChildren().add(images[i]);
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

    private int getKnobCount() {
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

    private void setKnobUIColorHex(int knob, String color) {
        var lightPane = (Shape) lightPanes.getChildren().get(knob);
        lightPane.setFill(Paint.valueOf(color));
    }

    private void setKnobUIColor(int knob, Paint color) {
        var lightPane = (Shape) lightPanes.getChildren().get(knob);
        lightPane.setFill(color);
    }

    private void setAllKnobUIColor(Paint color) {
        for (var i = 0; i < getKnobCount(); i++) {
            setKnobUIColor(i, color);
        }
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
        return DeviceType.PCPANEL_MINI;
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
        } else {
            var knobConfigs = config.getKnobConfigs();
            for (var i = 0; i < KNOB_COUNT; i++) {
                var knobConfig = knobConfigs[i];
                if (knobConfig.getMode() == SINGLE_KNOB_MODE.STATIC) {
                    setKnobUIColorHex(i, knobConfig.getColor1());
                } else if (knobConfig.getMode() == SINGLE_KNOB_MODE.VOLUME_GRADIENT) {
                    var c1 = Color.web(knobConfig.getColor1());
                    var c2 = Color.web(knobConfig.getColor2());
                    setKnobUIColor(i, c1.interpolate(c2, analogValue[i] / MAX_ANALOG_VALUE));
                }
            }
        }
    }

    @Override
    protected ImageView[] getKnobImages() {
        return images;
    }
}
