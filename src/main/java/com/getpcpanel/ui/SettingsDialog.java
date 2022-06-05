package com.getpcpanel.ui;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.getpcpanel.Json;
import com.getpcpanel.obs.OBSListener;
import com.getpcpanel.obs.remote.OBSRemoteController;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.util.FileUtil;

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@RequiredArgsConstructor
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SettingsDialog extends Application implements Initializable {
    private final SaveService saveService;
    private final Json json;
    private final OBSListener obsListener;
    private final FileUtil fileUtil;
    private final FxHelper fxHelper;
    private final Stage parentStage;

    private Stage stage;
    private Pane pane;
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

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        var loader = fxHelper.getLoader(getClass().getResource("/assets/SettingsDialog.fxml"));
        loader.setController(this);
        try {
            pane = loader.load();
        } catch (IOException e) {
            log.error("Unable to load loader", e);
        }
        var scene = new Scene(pane);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/assets/dark_theme.css"), "Unable to find dark_theme.css").toExternalForm());
        stage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResource("/assets/256x256.png")).toExternalForm()));
        stage.setScene(scene);
        stage.setResizable(false);
        stage.sizeToScene();
        stage.setTitle("Settings");
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(parentStage);
        stage.showAndWait();
    }

    @FXML
    private void onOBSEnablePressed(ActionEvent ignored) {
        obsControls.setDisable(!obsEnable.isSelected());
    }

    @FXML
    private void onVMEnablePressed(ActionEvent ignored) {
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
        try {
            Runtime.getRuntime().exec("cmd /c \"start %s\"".formatted(fileUtil.getFile("logs").getAbsolutePath()));
        } catch (IOException e) {
            log.error("Unable to open logs folder", e);
        }
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
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        obsLink.setOnAction(c -> getHostServices().showDocument(obsLink.getText()));
        initFields();
    }
}
