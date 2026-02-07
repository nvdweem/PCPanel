package com.getpcpanel.ui;

import static com.getpcpanel.profile.Save.DEFAULT_OVERLAY_BAR_BACKGROUND_COLOR;
import static com.getpcpanel.profile.Save.DEFAULT_OVERLAY_BAR_COLOR;
import static com.getpcpanel.profile.Save.DEFAULT_OVERLAY_BAR_HEIGHT;
import static com.getpcpanel.profile.Save.DEFAULT_OVERLAY_BG_COLOR;
import static com.getpcpanel.profile.Save.DEFAULT_OVERLAY_PADDING;
import static com.getpcpanel.profile.Save.DEFAULT_OVERLAY_TEXT_COLOR;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Component;

import com.getpcpanel.MainFX;
import com.getpcpanel.cpp.linux.pulseaudio.SndCtrlPulseAudioDebug;
import com.getpcpanel.cpp.windows.SndCtrlWindows;
import com.getpcpanel.obs.OBS;
import com.getpcpanel.profile.Save;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.WaveLinkSettings;
import com.getpcpanel.spring.OsHelper;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.UIInitializer.SingleParamInitializer;
import com.getpcpanel.util.FileUtil;
import com.getpcpanel.util.IPlatformCommand;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
public class SettingsDialog extends Application implements UIInitializer<SingleParamInitializer<Stage>> {
    private final SaveService saveService;
    private final FileUtil fileUtil;
    private final IPlatformCommand platformCommand;
    private final OsHelper osHelper;
    private final OBS obs;
    private Stage parentStage;

    private Stage stage;
    @FXML private Pane root;
    @FXML private CheckBox mainUiIcons;
    @FXML private CheckBox startupVersionCheck;
    @FXML private CheckBox overlay;
    @FXML private CheckBox overlayUseLog;
    @FXML private CheckBox overlayShowNumber;
    @FXML private ColorPicker overlayTextColor;
    @FXML private ColorPicker overlayBackgroundColor;
    @FXML private ColorPicker overlayBarColor;
    @FXML private ColorPicker overlayBarBackgroundColor;
    @FXML private TextField overlayWindowCornerRounding;
    @FXML private TextField overlayBarHeight;
    @FXML private TextField overlayBarCornerRounding;
    @FXML private Label overlayBGTransparency;
    @FXML private TextField dblClickInterval;
    @FXML private CheckBox preventClickWhenDblClick;
    @FXML private CheckBox obsEnable;
    @FXML private Pane obsControls;
    @FXML private TextField obsAddress;
    @FXML private TextField obsPort;
    @FXML private TextField obsPassword;
    @FXML private Button testBtn;
    @FXML private Label obsTestResult;
    @FXML private CheckBox vmEnable;
    @FXML private Pane vmControls;
    @FXML private TextField vmPath;
    @FXML private Tab voicemeeterTab;
    @FXML private Tab waveLinkTab;
    @FXML private CheckBox waveLinkEnable;
    @FXML private TextField txtPreventSliderTwitch;
    @FXML private TextField txtSliderRollingAverage;
    @FXML private TextField txtOnlyIfDelta;
    @FXML private CheckBox cbFixOnlySliders;
    @FXML private OSCSettingsDialog oscSettingsController;
    @FXML private MqttSettingsDialog mqttSettingsController;
    @FXML private VBox debug;
    @FXML private Label copied;
    @FXML private ToggleButton btnTL;
    @FXML private ToggleButton btnTM;
    @FXML private ToggleButton btnTR;
    @FXML private ToggleButton btnML;
    @FXML private ToggleButton btnMM;
    @FXML private ToggleButton btnMR;
    @FXML private ToggleButton btnBL;
    @FXML private ToggleButton btnBM;
    @FXML private ToggleButton btnBR;
    @FXML public TextField overlayPadding;

    @Override
    public void initUI(@Nonnull SingleParamInitializer<Stage> args) {
        parentStage = args.param();
        postInit();
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        UIHelper.closeOnEscape(stage);
        var scene = new Scene(root);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/assets/dark_theme.css"), "Unable to find dark_theme.css").toExternalForm());
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/assets/1.css"), "Unable to find 1.css").toExternalForm());
        stage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResource("/assets/256x256.png")).toExternalForm()));
        stage.setScene(scene);
        stage.setResizable(false);
        stage.sizeToScene();
        stage.setTitle("Settings");
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(parentStage);

        if (osHelper.notWindows()) {
            voicemeeterTab.getTabPane().getTabs().remove(voicemeeterTab);
        }
        if (osHelper.isLinux()) { // MacOS is supported by WaveLink
            waveLinkTab.getTabPane().getTabs().remove(waveLinkTab);
        }

        stage.showAndWait();
    }

    @FXML
    private void onOBSEnablePressed(@Nullable ActionEvent ignored) {
        obsControls.setDisable(!obsEnable.isSelected());
    }

    @FXML
    private void onVMEnablePressed(@Nullable ActionEvent ignored) {
        vmControls.setDisable(!vmEnable.isSelected());
    }

    @FXML
    private void onVoiceMeeterBrowse(ActionEvent event) {
        UIHelper.showFolderPicker("Pick VoiceMeeter directory", vmPath);
    }

    @FXML
    private void ok(ActionEvent event) {
        var save = saveService.get();
        save.setMainUIIcons(mainUiIcons.isSelected());
        save.setStartupVersionCheck(startupVersionCheck.isSelected());
        save.setOverlayEnabled(overlay.isSelected());
        save.setOverlayUseLog(overlayUseLog.isSelected());
        save.setOverlayShowNumber(overlayShowNumber.isSelected());
        save.setOverlayBackgroundColor(toWebColor(overlayBackgroundColor.getValue()));
        save.setOverlayBarColor(toWebColor(overlayBarColor.getValue()));
        save.setOverlayBarBackgroundColor(toWebColor(overlayBarBackgroundColor.getValue()));
        save.setOverlayTextColor(toWebColor(overlayTextColor.getValue()));
        save.setOverlayWindowCornerRounding(NumberUtils.toInt(overlayWindowCornerRounding.getText(), 0));
        save.setOverlayBarHeight(NumberUtils.toInt(overlayBarHeight.getText(), 0));
        save.setOverlayBarCornerRounding(NumberUtils.toInt(overlayBarCornerRounding.getText(), 0));
        save.setOverlayPadding(NumberUtils.toInt(overlayPadding.getText(), 0));
        save.setOverlayPosition(getOverlayPosition());
        save.setDblClickInterval(NumberUtils.toLong(dblClickInterval.getText(), 500));
        save.setPreventClickWhenDblClick(preventClickWhenDblClick.isSelected());
        save.setObsEnabled(obsEnable.isSelected());
        save.setObsAddress(obsAddress.getText());
        save.setObsPort(obsPort.getText());
        save.setObsPassword(obsPassword.getText());
        save.setVoicemeeterEnabled(vmEnable.isSelected());
        save.setVoicemeeterPath(vmPath.getText());
        save.setWaveLink(new WaveLinkSettings(waveLinkEnable.isSelected()));
        save.setPreventSliderTwitchDelay(NumberUtils.toInt(txtPreventSliderTwitch.getText(), 0));
        save.setSliderRollingAverage(NumberUtils.toInt(txtSliderRollingAverage.getText(), 0));
        save.setSendOnlyIfDelta(NumberUtils.toInt(txtOnlyIfDelta.getText(), 0));
        save.setWorkaroundsOnlySliders(cbFixOnlySliders.isSelected());
        oscSettingsController.save(save);
        mqttSettingsController.save(save);
        saveService.save();
        stage.close();
    }

    private String toWebColor(@Nonnull Color value) {
        return "rgba(%s, %s, %s, %s)".formatted(
                Math.round(value.getRed() * 255),
                Math.round(value.getGreen() * 255),
                Math.round(value.getBlue() * 255),
                Math.round(value.getOpacity() * 100) / 100f
        );
    }

    @FXML
    private void closeButtonAction(ActionEvent event) {
        stage.close();
    }

    @FXML
    private void openLogsFolder(ActionEvent event) {
        var logFolder = fileUtil.getFile("logs");
        platformCommand.exec(logFolder.getAbsolutePath());
    }

    private void initFields() {
        var save = saveService.get();
        mainUiIcons.setSelected(save.isMainUIIcons());
        startupVersionCheck.setSelected(save.isStartupVersionCheck());
        overlay.setSelected(save.isOverlayEnabled());
        overlayUseLog.setSelected(save.isOverlayUseLog());
        overlayShowNumber.setSelected(save.isOverlayShowNumber());
        overlayWindowCornerRounding.setText("" + save.getOverlayWindowCornerRounding());
        overlayBarHeight.setText("" + save.getOverlayBarHeight());
        overlayBarCornerRounding.setText("" + save.getOverlayBarCornerRounding());
        initOverlayPosition(save);
        overlayPadding.setText("" + save.getOverlayPadding());
        dblClickInterval.setText(save.getDblClickInterval() == null ? "500" : save.getDblClickInterval().toString());
        preventClickWhenDblClick.setSelected(save.isPreventClickWhenDblClick());
        obsEnable.setSelected(save.isObsEnabled());
        obsAddress.setText(save.getObsAddress());
        obsPort.setText(save.getObsPort());
        obsPassword.setText(save.getObsPassword());
        onOBSEnablePressed(null);
        vmEnable.setSelected(save.isVoicemeeterEnabled());
        vmPath.setText(save.getVoicemeeterPath());
        waveLinkEnable.setSelected(save.getWaveLink().enabled());
        onVMEnablePressed(null);
        txtPreventSliderTwitch.setText(save.getPreventSliderTwitchDelay() == null ? "" : save.getPreventSliderTwitchDelay().toString());
        txtSliderRollingAverage.setText(save.getSliderRollingAverage() == null ? "" : save.getSliderRollingAverage().toString());
        txtOnlyIfDelta.setText(save.getSendOnlyIfDelta() == null ? "" : save.getSendOnlyIfDelta().toString());
        cbFixOnlySliders.setSelected(save.isWorkaroundsOnlySliders());

        oscSettingsController.populate(save);
        mqttSettingsController.populate(save);
        initOverlayColors(save);
    }

    private void initOverlayPosition(Save save) {
        var position = save.getOverlayPosition();
        btnTL.setSelected(position == OverlayPosition.topLeft);
        btnTM.setSelected(position == OverlayPosition.topMiddle);
        btnTR.setSelected(position == OverlayPosition.topRight);
        btnML.setSelected(position == OverlayPosition.middleLeft);
        btnMM.setSelected(position == OverlayPosition.middleMiddle);
        btnMR.setSelected(position == OverlayPosition.middleRight);
        btnBL.setSelected(position == OverlayPosition.bottomLeft);
        btnBM.setSelected(position == OverlayPosition.bottomMiddle);
        btnBR.setSelected(position == OverlayPosition.bottomRight);
    }

    private OverlayPosition getOverlayPosition() {
        // @formatter:off
        if (btnTL.isSelected())
            return OverlayPosition.topLeft;
        if (btnTM.isSelected())
            return OverlayPosition.topMiddle;
        if (btnTR.isSelected())
            return OverlayPosition.topRight;
        if (btnML.isSelected())
            return OverlayPosition.middleLeft;
        if (btnMM.isSelected())
            return OverlayPosition.middleMiddle;
        if (btnMR.isSelected())
            return OverlayPosition.middleRight;
        if (btnBL.isSelected())
            return OverlayPosition.bottomLeft;
        if (btnBM.isSelected())
            return OverlayPosition.bottomMiddle;
        if (btnBR.isSelected())
            return OverlayPosition.bottomRight;
        // @formatter:on
        return OverlayPosition.topLeft;
    }

    private void initOverlayColors(Save save) {
        try {
            overlayBackgroundColor.setValue(Color.web(save.getOverlayBackgroundColor()));
        } catch (IllegalArgumentException e) {
            overlayBackgroundColor.setValue(Color.web(DEFAULT_OVERLAY_BG_COLOR));
        }
        try {
            overlayTextColor.setValue(Color.web(save.getOverlayTextColor()));
        } catch (IllegalArgumentException e) {
            overlayTextColor.setValue(Color.web(DEFAULT_OVERLAY_TEXT_COLOR));
        }
        try {
            overlayBarColor.setValue(Color.web(save.getOverlayBarColor()));
        } catch (IllegalArgumentException e) {
            overlayBarColor.setValue(Color.web(DEFAULT_OVERLAY_BAR_COLOR));
        }
        try {
            overlayBarBackgroundColor.setValue(Color.web(save.getOverlayBarBackgroundColor()));
        } catch (IllegalArgumentException e) {
            overlayBarBackgroundColor.setValue(Color.web(DEFAULT_OVERLAY_BAR_BACKGROUND_COLOR));
        }

        var colorOpacityBinding = Bindings.createObjectBinding(() -> Math.round(overlayBackgroundColor.getValue().getOpacity() * 100) + "%", overlayBackgroundColor.valueProperty());
        overlayBGTransparency.textProperty().bind(colorOpacityBinding);
    }

    private void postInit() {
        initFields();
        osHelper.hideUnsupportedChildren(debug.getChildrenUnmodifiable());
    }

    public void doTest(ActionEvent ignored) {
        obsTestResult.setText("Testing...");
        testBtn.setDisable(true);
        new Thread(() -> {
            var port = NumberUtils.toInt(obsPort.getText(), -1);
            String result;
            if (port == -1) {
                result = "Invalid port";
            } else {
                var message = obs.test(obsAddress.getText(), port, obsPassword.getText(), 2_500L);
                if (message == null) {
                    result = "Success";
                } else {
                    result = "Failed: " + message;
                }
            }
            Platform.runLater(() -> {
                testBtn.setDisable(false);
                obsTestResult.setText(result);
            });
        }).start();
    }

    @SuppressWarnings("unused")
    public void triggerAv(ActionEvent ignored) {
        MainFX.getBean(SndCtrlWindows.class).triggerAv();
    }

    public void copyAudioOutput(ActionEvent ignored) {
        copied.setText("Preparing output");
        MainFX.getOptionalBean(SndCtrlPulseAudioDebug.class).ifPresent(SndCtrlPulseAudioDebug::copyDebugOutput);
        copied.setText("Output was copied to your clipboard");
    }

    public void resetOverlayToDefault(ActionEvent actionEvent) {
        overlayUseLog.setSelected(false);
        overlayShowNumber.setSelected(false);
        btnTL.setSelected(true);
        overlayWindowCornerRounding.setText("0");
        overlayBarHeight.setText(DEFAULT_OVERLAY_BAR_HEIGHT + "");
        overlayBarCornerRounding.setText("0");
        overlayPadding.setText(DEFAULT_OVERLAY_PADDING + "");
        overlayBackgroundColor.setValue(Color.web(DEFAULT_OVERLAY_BG_COLOR));
        overlayTextColor.setValue(Color.web(DEFAULT_OVERLAY_TEXT_COLOR));
        overlayBarColor.setValue(Color.web(DEFAULT_OVERLAY_BAR_COLOR));
        overlayBarBackgroundColor.setValue(Color.web(DEFAULT_OVERLAY_BAR_BACKGROUND_COLOR));
    }
}
