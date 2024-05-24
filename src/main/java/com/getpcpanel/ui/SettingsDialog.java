package com.getpcpanel.ui;

import static com.getpcpanel.profile.Save.DEFAULT_OVERLAY_BG_COLOR;
import static com.getpcpanel.profile.Save.DEFAULT_OVERLAY_TEXT_COLOR;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Component;

import com.getpcpanel.MainFX;
import com.getpcpanel.cpp.linux.SndCtrlLinuxDebug;
import com.getpcpanel.cpp.windows.SndCtrlWindows;
import com.getpcpanel.obs.OBS;
import com.getpcpanel.profile.Save;
import com.getpcpanel.profile.SaveService;
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
    @FXML public TextField overlayCornerRounding;
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
    @FXML private TextField txtPreventSliderTwitch;
    @FXML private TextField txtSliderRollingAverage;
    @FXML private TextField txtOnlyIfDelta;
    @FXML private CheckBox cbFixOnlySliders;
    @FXML private OSCSettingsDialog oscSettingsController;
    @FXML private VBox debug;
    @FXML private Label copied;

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
        save.setOverlayTextColor(toWebColor(overlayTextColor.getValue()));
        save.setOverlayCornerRounding(NumberUtils.toInt(overlayCornerRounding.getText(), 0));
        save.setDblClickInterval(NumberUtils.toLong(dblClickInterval.getText(), 500));
        save.setPreventClickWhenDblClick(preventClickWhenDblClick.isSelected());
        save.setObsEnabled(obsEnable.isSelected());
        save.setObsAddress(obsAddress.getText());
        save.setObsPort(obsPort.getText());
        save.setObsPassword(obsPassword.getText());
        save.setVoicemeeterEnabled(vmEnable.isSelected());
        save.setVoicemeeterPath(vmPath.getText());
        save.setPreventSliderTwitchDelay(NumberUtils.toInt(txtPreventSliderTwitch.getText(), 0));
        save.setSliderRollingAverage(NumberUtils.toInt(txtSliderRollingAverage.getText(), 0));
        save.setSendOnlyIfDelta(NumberUtils.toInt(txtOnlyIfDelta.getText(), 0));
        save.setWorkaroundsOnlySliders(cbFixOnlySliders.isSelected());
        save.setOscListenPort(oscSettingsController.getListenPort());
        save.setOscConnections(oscSettingsController.getConnections());
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
        overlayCornerRounding.setText("" + save.getOverlayCornerRounding());
        dblClickInterval.setText(save.getDblClickInterval() == null ? "500" : save.getDblClickInterval().toString());
        preventClickWhenDblClick.setSelected(save.isPreventClickWhenDblClick());
        obsEnable.setSelected(save.isObsEnabled());
        obsAddress.setText(save.getObsAddress());
        obsPort.setText(save.getObsPort());
        obsPassword.setText(save.getObsPassword());
        onOBSEnablePressed(null);
        vmEnable.setSelected(save.isVoicemeeterEnabled());
        vmPath.setText(save.getVoicemeeterPath());
        onVMEnablePressed(null);
        txtPreventSliderTwitch.setText(save.getPreventSliderTwitchDelay() == null ? "" : save.getPreventSliderTwitchDelay().toString());
        txtSliderRollingAverage.setText(save.getSliderRollingAverage() == null ? "" : save.getSliderRollingAverage().toString());
        txtOnlyIfDelta.setText(save.getSendOnlyIfDelta() == null ? "" : save.getSendOnlyIfDelta().toString());
        cbFixOnlySliders.setSelected(save.isWorkaroundsOnlySliders());
        oscSettingsController.setConnections(save.getOscListenPort(), save.getOscConnections());

        initOverlayColors(save);
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
        MainFX.getBean(SndCtrlLinuxDebug.class).copyDebugOutput();
        copied.setText("Output was copied to your clipboard");
    }
}
