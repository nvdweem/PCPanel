package com.getpcpanel.ui;

import java.util.Objects;

import javax.annotation.Nullable;

import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Component;

import com.getpcpanel.MainFX;
import com.getpcpanel.cpp.windows.SndCtrlWindows;
import com.getpcpanel.obs.OBS;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.spring.OsHelper;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.util.FileUtil;
import com.getpcpanel.util.IPlatformCommand;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
public class SettingsDialog extends Application implements UIInitializer {
    private final SaveService saveService;
    private final FileUtil fileUtil;
    private final IPlatformCommand platformCommand;
    private final OsHelper osHelper;
    private final OBS obs;
    private Stage parentStage;

    private Stage stage;
    @FXML private Pane root;
    @FXML public CheckBox overlay;
    @FXML public CheckBox mainUiIcons;
    @FXML private CheckBox startupVersionCheck;
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
    @FXML public TextField txtPreventSliderTwitch;
    @FXML public TextField txtSliderRollingAverage;
    @FXML public TextField txtOnlyIfDelta;
    @FXML public CheckBox cbFixOnlySliders;
    @FXML private OSCSettingsDialog oscSettingsController;

    @Override
    public <T> void initUI(T... args) {
        parentStage = getUIArg(Stage.class, args, 0);
        postInit();
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
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
        save.setOverlayEnabled(overlay.isSelected());
        save.setMainUIIcons(mainUiIcons.isSelected());
        save.setStartupVersionCheck(startupVersionCheck.isSelected());
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
        overlay.setSelected(save.isOverlayEnabled());
        mainUiIcons.setSelected(save.isMainUIIcons());
        startupVersionCheck.setSelected(save.isStartupVersionCheck());
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
    }

    private void postInit() {
        initFields();
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
}
