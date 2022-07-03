package com.getpcpanel.ui;

import java.util.Objects;

import javax.annotation.Nullable;

import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Component;

import com.getpcpanel.Json;
import com.getpcpanel.obs.OBSListener;
import com.getpcpanel.obs.remote.OBSRemoteController;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.spring.OsHelper;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.util.FileUtil;
import com.getpcpanel.util.IPlatformCommand;

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
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
    private final Json json;
    private final OBSListener obsListener;
    private final FileUtil fileUtil;
    private final IPlatformCommand platformCommand;
    private final OsHelper osHelper;
    private Stage parentStage;

    private Stage stage;
    @FXML private Pane root;
    @FXML private CheckBox obsEnable;
    @FXML private Pane obsControls;
    @FXML private TextField obsAddress;
    @FXML private TextField obsPort;
    @FXML private TextField obsPassword;
    @FXML private Label obsTestResult;
    @FXML private Hyperlink obsLink;
    @FXML private CheckBox vmEnable;
    @FXML private Pane vmControls;
    @FXML private TextField vmPath;
    @FXML private Tab voicemeeterTab;
    @FXML public TextField txtPreventSliderTwitch;
    @FXML public TextField txtSliderRollingAverage;
    @FXML public TextField txtOnlyIfDelta;
    @FXML public CheckBox cbFixOnlySliders;

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
    private void obsTest(ActionEvent event) {
        var controller = new OBSRemoteController(json, obsAddress.getText(), obsPort.getText(), obsPassword.getText());
        if (controller.isFailed()) {
            obsTestResult.setText("result: connection failed");
        } else {
            obsTestResult.setText("result: success");
        }
        controller.disconnect();
    }

    @FXML
    private void onVoiceMeeterBrowse(ActionEvent event) {
        UIHelper.showFolderPicker("Pick VoiceMeeter directory", vmPath);
    }

    @FXML
    private void ok(ActionEvent event) {
        var save = saveService.get();
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
        saveService.save();
        obsListener.check();
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
    }

    private void postInit() {
        obsLink.setOnAction(c -> getHostServices().showDocument(obsLink.getText()));
        initFields();
    }
}
