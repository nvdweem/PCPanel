package com.getpcpanel.ui;

import static com.getpcpanel.commands.command.CommandNoOp.NOOP;

import java.util.HashMap;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandBrightness;
import com.getpcpanel.commands.command.CommandNoOp;
import com.getpcpanel.commands.command.CommandObsSetSourceVolume;
import com.getpcpanel.commands.command.CommandVoiceMeeterAdvanced;
import com.getpcpanel.commands.command.CommandVoiceMeeterBasic;
import com.getpcpanel.commands.command.CommandVolumeDevice;
import com.getpcpanel.commands.command.CommandVolumeFocus;
import com.getpcpanel.commands.command.CommandVolumeProcess;
import com.getpcpanel.device.Device;
import com.getpcpanel.profile.KnobSetting;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.dial.BrightnessController;
import com.getpcpanel.ui.command.dial.ObsController;
import com.getpcpanel.ui.command.dial.VoiceMeeterController;
import com.getpcpanel.ui.command.dial.VolumeDeviceController;
import com.getpcpanel.ui.command.dial.VolumeFocusController;
import com.getpcpanel.ui.command.dial.VolumeProcessController;
import com.getpcpanel.util.Util;

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
    private final FxHelper fxHelper;
    private Profile profile;

    @FXML private Pane root;
    @FXML private MacroButtonController singleClickPanelController;
    @FXML private MacroButtonController doubleClickPanelController;

    // Dials
    @FXML private VolumeProcessController dialCommandVolumeProcessController;
    @FXML private VolumeFocusController dialCommandVolumeFocusController;
    @FXML private VolumeDeviceController dialCommandVolumeDeviceController;
    @FXML private BrightnessController dialCommandBrightnessController;
    @FXML private ObsController dialCommandObsController;
    @FXML private VoiceMeeterController dialCommandVoiceMeeterController;

    @FXML private Pane topPane;
    @FXML private TabPane mainTabPane;
    @FXML private TabPane dialTabPane;
    @FXML private Button scFileButton;

    @FXML private TextField trimMin;
    @FXML private TextField trimMax;
    @FXML private TextField iconFld;
    @FXML private TextField buttonDebounceTime;
    @FXML private CheckBox logarithmic;
    private Stage stage;
    private Command volData;
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
        profile = deviceSave.ensureCurrentProfile(device.getDeviceType());
        volData = profile.getDialData(dialNum);
        knobSetting = profile.getKnobSettings(dialNum);

        // Do this before possibly removing the first 2 tabs
        if (analogType != null) {
            mainTabPane.getTabs().get(2).setText(analogType);
        }

        if (hasButton) {
            singleClickPanelController.initController(stage, deviceSave, profile, profile.getButtonData(dialNum));
            doubleClickPanelController.initController(stage, deviceSave, profile, profile.getDblButtonData(dialNum));
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
        // basicmacro.initOwner((Window)Window.stage);
        basicmacro.setScene(scene);
        basicmacro.sizeToScene();
        basicmacro.setTitle(Objects.requireNonNullElseGet(name, () -> "Knob " + (dialNum + 1)));
        basicmacro.show();
    }

    @FXML
    private void ok(ActionEvent event) {
        var dialType = fxHelper.getSelectedTabId(dialTabPane);
        var buttonData = singleClickPanelController.determineButtonCommand();
        var dblButtonData = doubleClickPanelController.determineButtonCommand();
        volData = determineVolCommand(dialType);
        knobSetting.setMinTrim(NumberUtils.toInt(trimMin.getText(), 0));
        knobSetting.setMaxTrim(NumberUtils.toInt(trimMax.getText(), 100));
        knobSetting.setOverlayIcon(iconFld.getText());
        knobSetting.setButtonDebounce(NumberUtils.toInt(buttonDebounceTime.getText(), 50));
        knobSetting.setLogarithmic(logarithmic.isSelected());
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

    private Command determineVolCommand(String dialType) {
        return switch (dialType) {
            case "dialCommandVolumeProcess" -> dialCommandVolumeProcessController.buildCommand();
            case "dialCommandVolumeFocus" -> dialCommandVolumeFocusController.buildCommand();
            case "dialCommandVolumeDevice" -> dialCommandVolumeDeviceController.buildCommand();
            case "dialCommandObs" -> dialCommandObsController.buildCommand();
            case "dialCommandVoiceMeeter" -> dialCommandVoiceMeeterController.buildCommand();
            case "dialCommandBrightness" -> dialCommandBrightnessController.buildCommand();
            default -> NOOP;
        };
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
        Util.adjustTabs(dialTabPane, 150, 30);
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

        // Init controllers
        dialCommandVolumeProcessController.postInit(stage, volData);
        dialCommandVolumeFocusController.postInit(stage, volData);
        dialCommandVolumeDeviceController.postInit(stage, volData);
        dialCommandBrightnessController.postInit(stage, volData);
        dialCommandObsController.postInit(stage, volData);
        dialCommandVoiceMeeterController.postInit(stage, volData);
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
        initDialFields();

        if (knobSetting != null) {
            trimMin.setText(String.valueOf(knobSetting.getMinTrim()));
            trimMax.setText(String.valueOf(knobSetting.getMaxTrim()));
            iconFld.setText(StringUtils.defaultString(knobSetting.getOverlayIcon(), ""));
            buttonDebounceTime.setText(String.valueOf(knobSetting.getButtonDebounce()));
            logarithmic.setSelected(knobSetting.isLogarithmic());
        }
    }

    private void initDialFields() {
        if (volData == null || volData.equals(NOOP))
            return;
        fxHelper.selectTabById(dialTabPane, "dial" + volData.getClass().getSimpleName());
        fxHelper.selectTabById(dialTabPane, "dial" + volData.getClass().getSuperclass().getSimpleName());

        //noinspection unchecked,rawtypes
        ((Consumer) getDialInitializer().getOrDefault(volData.getClass(), x -> log.error("No initializer for {}", x))).accept(volData); // Yuck :(
    }

    private HashMap<Class<? extends Command>, Consumer<?>> getDialInitializer() {
        var dialInitializers = new HashMap<Class<? extends Command>, Consumer<?>>(); // Blegh
        dialInitializers.put(CommandNoOp.class, (CommandNoOp command) -> {
        });
        dialInitializers.put(CommandVolumeProcess.class, (CommandVolumeProcess cmd) -> dialCommandVolumeProcessController.initFromCommand(cmd));
        dialInitializers.put(CommandVolumeFocus.class, (CommandVolumeFocus cmd) -> dialCommandVolumeFocusController.initFromCommand(cmd));
        dialInitializers.put(CommandVolumeDevice.class, (CommandVolumeDevice cmd) -> dialCommandVolumeDeviceController.initFromCommand(cmd));
        dialInitializers.put(CommandBrightness.class, (CommandBrightness cmd) -> dialCommandBrightnessController.initFromCommand(cmd));
        dialInitializers.put(CommandObsSetSourceVolume.class, (CommandObsSetSourceVolume cmd) -> dialCommandObsController.initFromCommand(cmd));
        dialInitializers.put(CommandVoiceMeeterBasic.class, (CommandVoiceMeeterBasic cmd) -> dialCommandVoiceMeeterController.initFromCommand(cmd));
        dialInitializers.put(CommandVoiceMeeterAdvanced.class, (CommandVoiceMeeterAdvanced cmd) -> dialCommandVoiceMeeterController.initFromCommand(cmd));
        return dialInitializers;
    }
}
