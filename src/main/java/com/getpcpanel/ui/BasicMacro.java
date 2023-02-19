package com.getpcpanel.ui;

import java.util.Objects;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Component;

import com.getpcpanel.device.Device;
import com.getpcpanel.profile.KnobSetting;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.ButtonController;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.CommandContext;

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TabPane;
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
public class BasicMacro extends Application implements UIInitializer {
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d*");
    private static final Pattern NOT_NUMBER_PATTERN = Pattern.compile("[^\\d]");
    private final SaveService saveService;
    private CommandContext context;

    @FXML private Pane root;
    @FXML private ButtonController singleClickPanelController;
    @FXML private ButtonController doubleClickPanelController;
    @FXML private ButtonController dialPanelController;

    @FXML private Pane topPane;
    @FXML private TabPane mainTabPane;
    @FXML private Button scFileButton;

    @FXML private TextField trimMin;
    @FXML private TextField trimMax;
    @FXML private TextField iconFld;
    @FXML private TextField buttonDebounceTime;
    @FXML private CheckBox logarithmic;
    private Stage stage;
    private int dialNum;
    private KnobSetting knobSetting;
    private String name;

    @Override
    public <T> void initUI(T... args) {
        var device = getUIArg(Device.class, args, 0);
        dialNum = getUIArg(Integer.class, args, 1);
        var hasButton = getUIArg(Boolean.class, args, 2, true);
        name = getUIArg(String.class, args, 3);
        var analogType = getUIArg(String.class, args, 4);

        var deviceSave = saveService.get().getDeviceSave(device.getSerialNumber());
        var profile = deviceSave.ensureCurrentProfile(device.getDeviceType());
        knobSetting = profile.getKnobSettings(dialNum);

        // Do this before possibly removing the first 2 tabs
        if (analogType != null) {
            mainTabPane.getTabs().get(2).setText(analogType);
        }
        dialPanelController.initController(Cmd.Type.dial, context, profile.getDialData(dialNum));

        context = new CommandContext(stage, deviceSave, profile);
        if (hasButton) {
            singleClickPanelController.initController(Cmd.Type.button, context, profile.getButtonData(dialNum));
            doubleClickPanelController.initController(Cmd.Type.button, context, profile.getDblButtonData(dialNum));
        } else {
            mainTabPane.getTabs().remove(0);
            mainTabPane.getTabs().remove(0);
        }

        postInit();
    }

    @Override
    public void start(Stage basicmacro) throws Exception {
        stage = basicmacro;
        var scene = new Scene(root);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/assets/dark_theme.css")).toExternalForm());
        basicmacro.getIcons().add(new Image(Objects.requireNonNull(getClass().getResource("/assets/256x256.png")).toExternalForm()));
        basicmacro.initModality(Modality.APPLICATION_MODAL);
        basicmacro.setScene(scene);
        basicmacro.sizeToScene();
        basicmacro.setTitle(Objects.requireNonNullElseGet(name, () -> "Knob " + (dialNum + 1)));
        basicmacro.show();
    }

    @FXML
    private void ok(ActionEvent event) {
        var buttonData = singleClickPanelController.determineButtonCommand();
        var dblButtonData = doubleClickPanelController.determineButtonCommand();
        var volData = dialPanelController.determineButtonCommand();
        knobSetting.setMinTrim(NumberUtils.toInt(trimMin.getText(), 0));
        knobSetting.setMaxTrim(NumberUtils.toInt(trimMax.getText(), 100));
        knobSetting.setOverlayIcon(iconFld.getText());
        knobSetting.setButtonDebounce(NumberUtils.toInt(buttonDebounceTime.getText(), 50));
        knobSetting.setLogarithmic(logarithmic.isSelected());

        var profile = context.profile();
        profile.setButtonData(dialNum, buttonData);
        profile.setDblButtonData(dialNum, dblButtonData);
        profile.setDialData(dialNum, volData);
        if (log.isDebugEnabled()) {
            log.debug("-----------------");
            log.debug(buttonData);
            log.debug(dblButtonData);
            log.debug(volData);
            log.debug("-----------------");
        }
        saveService.save();
        stage.close();
    }

    @FXML
    public void iconFile(ActionEvent event) {
        UIHelper.showFilePicker("Pick file", iconFld);
    }

    @FXML
    private void closeButtonAction() {
        stage.close();
    }

    private void postInit() {
        trimMin.textProperty().addListener((observable, oldValue, newValue) -> trimMinMax(oldValue, newValue, trimMin));
        trimMax.textProperty().addListener((observable, oldValue, newValue) -> trimMinMax(oldValue, newValue, trimMax));
        buttonDebounceTime.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!NUMBER_PATTERN.matcher(newValue).matches() || newValue.contains("-") || StringUtils.isBlank(newValue)) {
                buttonDebounceTime.setText(NOT_NUMBER_PATTERN.matcher(newValue.replace("-", "")).replaceAll(""));
            } else {
                var num = Integer.parseInt(newValue);
                if (num < 0 || num > 10000) {
                    buttonDebounceTime.setText(oldValue);
                    return;
                }
                buttonDebounceTime.setText(String.valueOf(num));
            }
        });

        try {
            initFields();
        } catch (Exception e) {
            log.error("Unable to init fields", e);
        }
    }

    private void trimMinMax(String oldValue, String newValue, TextField trimMin) {
        if (!NUMBER_PATTERN.matcher(newValue).matches() || newValue.contains("-") || StringUtils.isBlank(newValue)) {
            trimMin.setText(NOT_NUMBER_PATTERN.matcher(newValue.replace("-", "")).replaceAll(""));
        } else {
            var num = Integer.parseInt(newValue);
            if (num < 0 || num > 100) {
                trimMin.setText(oldValue);
                return;
            }
            trimMin.setText(String.valueOf(num));
        }
    }

    private void initFields() {
        if (knobSetting != null) {
            trimMin.setText(String.valueOf(knobSetting.getMinTrim()));
            trimMax.setText(String.valueOf(knobSetting.getMaxTrim()));
            iconFld.setText(StringUtils.defaultString(knobSetting.getOverlayIcon(), ""));
            buttonDebounceTime.setText(String.valueOf(knobSetting.getButtonDebounce()));
            logarithmic.setSelected(knobSetting.isLogarithmic());
        }
    }
}
