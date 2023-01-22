package com.getpcpanel.device;

import java.io.IOException;
import java.util.Objects;

import javax.annotation.Nullable;

import org.springframework.context.ApplicationEventPublisher;

import com.getpcpanel.commands.IconService;
import com.getpcpanel.hid.DeviceCommunicationHandler;
import com.getpcpanel.hid.InputInterpreter;
import com.getpcpanel.hid.OutputInterpreter;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.LightingConfig;
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
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class PCPanelMapleUI extends Device {
    private final InputInterpreter inputInterpreter;

    public static final int KNOB_COUNT = 4;
    @FXML private Pane lightPanes;
    @FXML private Pane panelPane;
    private Label label;
    private final Button[] knobs = new Button[KNOB_COUNT];
    private final int[] analogValue = new int[KNOB_COUNT];
    private final ImageView[] images = new ImageView[KNOB_COUNT];
    private static final Image previewImage = new Image(Objects.requireNonNull(PCPanelMapleUI.class.getResource("/assets/PCPanelMaple/preview.png")).toExternalForm());
    private Stage childDialogStage;

    public PCPanelMapleUI(FxHelper fxHelper, InputInterpreter inputInterpreter, SaveService saveService, OutputInterpreter outputInterpreter, IconService iconService, ApplicationEventPublisher eventPublisher, String serialNum,
            DeviceSave deviceSave) {
        super(fxHelper, saveService, outputInterpreter, iconService, eventPublisher, serialNum, deviceSave);
        this.inputInterpreter = inputInterpreter;
        var loader = getFxHelper().getLoader(getClass().getResource("/assets/PCPanelMaple/PCPanelMaple.fxml"));
        loader.setController(this);
        try {
            Pane pane = loader.load();
            initButtons();
            initLabel();
            pane.getStylesheets().addAll(Objects.requireNonNull(getClass().getResource("/assets/PCPanelMaple/PCPanelMaple.css")).toExternalForm());
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
        ((Region) knobs[knob].getGraphic()).getChildrenUnmodifiable().get(3).setRotate(Util.analogValueToRotation(val));
    }

    @Override
    public int getKnobRotation(int knob) {
        return analogValue[knob];
    }

    private void initLabel() {
        label = new Label("PCPANEL Maple");
        var f = Font.loadFont(getClass().getResourceAsStream("/assets/apex-mk2.regular.otf"), 50.0D);
        label.setFont(f);
        label.setUnderline(true);
        label.setTextFill(Paint.valueOf("white"));
    }

    private void initButtons() throws IOException {
        var xPos = 56.3D;
        var yPos = 133.4D;
        var xDelta = 115.0D;
        var buttonSize = 80;
        for (var i = 0; i < KNOB_COUNT; i++) {
            var loader = getFxHelper().getLoader(getClass().getResource("/assets/PCPanelMaple/knob.fxml"));
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
    public boolean hasLighting() {
        return false;
    }

    @Override
    public @Nullable Button getLightingButton() {
        return null;
    }

    @Override
    public DeviceType getDeviceType() {
        return DeviceType.PCPANEL_MAPLE;
    }

    @Override
    public void showLightingConfigToUI(LightingConfig config) {
        // No lighting config available for Maple
    }

    @Override
    protected ImageView[] getKnobImages() {
        return images;
    }
}
