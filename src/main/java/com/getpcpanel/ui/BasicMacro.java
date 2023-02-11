package com.getpcpanel.ui;

import static com.getpcpanel.commands.command.CommandNoOp.NOOP;

import java.util.Collection;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandBrightness;
import com.getpcpanel.commands.command.CommandNoOp;
import com.getpcpanel.commands.command.CommandObsSetSourceVolume;
import com.getpcpanel.commands.command.CommandVoiceMeeter;
import com.getpcpanel.commands.command.CommandVoiceMeeterAdvanced;
import com.getpcpanel.commands.command.CommandVoiceMeeterBasic;
import com.getpcpanel.commands.command.CommandVolumeDevice;
import com.getpcpanel.commands.command.CommandVolumeFocus;
import com.getpcpanel.commands.command.CommandVolumeProcess;
import com.getpcpanel.cpp.AudioDevice;
import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.device.Device;
import com.getpcpanel.obs.OBS;
import com.getpcpanel.profile.KnobSetting;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.util.Util;
import com.getpcpanel.voicemeeter.Voicemeeter;
import com.getpcpanel.voicemeeter.Voicemeeter.ControlType;
import com.getpcpanel.voicemeeter.Voicemeeter.DialControlMode;
import com.getpcpanel.voicemeeter.Voicemeeter.DialType;

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
public class BasicMacro extends Application implements UIInitializer {
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d*");
    private static final Pattern NOT_NUMBER_PATTERN = Pattern.compile("[^\\d]");
    private final SaveService saveService;
    private final OBS obs;
    private final Voicemeeter voiceMeeter;
    private final ISndCtrl sndCtrl;
    private final FxHelper fxHelper;
    private Profile profile;

    @FXML private Pane root;
    @FXML private MacroButtonController singleClickPanelController;

    @FXML private Pane topPane;
    @FXML private TabPane mainTabPane;
    @FXML private TabPane dialTabPane;
    @FXML private Button scFileButton;

    @FXML private RadioButton rdio_app_output_specific;
    @FXML private ChoiceBox<AudioDevice> app_vol_output_device;
    @FXML private RadioButton rdio_device_specific;
    @FXML private ChoiceBox<AudioDevice> volumedevice;

    @FXML private RadioButton obsMuteToggle;
    @FXML private ChoiceBox<ControlType> voicemeeterBasicDialIO;
    @FXML private ChoiceBox<Integer> voicemeeterBasicDialIndex;
    @FXML private ChoiceBox<DialType> voicemeeterBasicDial;
    @FXML private PickProcessesController appVolumeController;
    @FXML private CheckBox cb_app_unmute;
    @FXML private RadioButton rdio_app_output_default;
    @FXML private RadioButton rdio_app_output_all;
    @FXML private CheckBox cb_device_unmute;
    @FXML private RadioButton rdio_device_default;
    @FXML private ChoiceBox<String> obsAudioSources;
    @FXML private TabPane voicemeeterTabPaneDial;
    @FXML private TextField voicemeeterDialParameter;
    @FXML private ChoiceBox<DialControlMode> voicemeeterDialType;
    @FXML private TextField trimMin;
    @FXML private TextField trimMax;
    @FXML private TextField iconFld;
    @FXML private TextField buttonDebounceTime;
    @FXML private CheckBox logarithmic;
    private Stage stage;
    private Command volData;
    private int dialNum;
    private KnobSetting knobSetting;
    private Collection<AudioDevice> allSoundDevices;
    private boolean hasButton;
    private String name;
    private String analogType;

    @Override
    public <T> void initUI(T... args) {
        var device = getUIArg(Device.class, args, 0);
        dialNum = getUIArg(Integer.class, args, 1);
        hasButton = getUIArg(Boolean.class, args, 2, true);
        name = getUIArg(String.class, args, 3);
        analogType = getUIArg(String.class, args, 4);

        var deviceSave = saveService.get().getDeviceSave(device.getSerialNumber());
        profile = deviceSave.ensureCurrentProfile(device.getDeviceType());
        singleClickPanelController.initController(stage, hasButton, deviceSave, profile, dialNum);
        volData = profile.getDialData(dialNum);
        knobSetting = profile.getKnobSettings(dialNum);
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

    private @Nonnull String getSelectedTabId(TabPane tabPane) {
        var tab = tabPane.getSelectionModel().getSelectedItem();
        return (tab == null) ? "" : tab.getId();
    }

    @FXML
    private void ok(ActionEvent event) {
        var buttonType = getSelectedTabId(singleClickPanelController.getRoot());
        var dialType = getSelectedTabId(dialTabPane);
        var buttonData = singleClickPanelController.determineButtonCommand(buttonType);
        volData = determineVolCommand(dialType);
        knobSetting.setMinTrim(NumberUtils.toInt(trimMin.getText(), 0));
        knobSetting.setMaxTrim(NumberUtils.toInt(trimMax.getText(), 100));
        knobSetting.setOverlayIcon(iconFld.getText());
        knobSetting.setButtonDebounce(NumberUtils.toInt(buttonDebounceTime.getText(), 50));
        knobSetting.setLogarithmic(logarithmic.isSelected());
        if (hasButton)
            profile.setButtonData(dialNum, buttonData);
        profile.setDialData(dialNum, volData);
        if (log.isDebugEnabled()) {
            log.debug("-----------------");
            log.debug(buttonData);
            log.debug(volData);
            log.debug("-----------------");
        }
        saveService.save();
        stage.close();
    }

    private Command determineVolCommand(String dialType) {
        return switch (dialType) {
            case "dialCommandVolumeProcess" -> {
                var device =
                        rdio_app_output_all.isSelected() ? "*" :
                                rdio_app_output_specific.isSelected() ? Optional.ofNullable(app_vol_output_device.getSelectionModel().getSelectedItem()).map(AudioDevice::id).orElse("") :
                                        "";
                yield new CommandVolumeProcess(appVolumeController.getSelection(), device, cb_app_unmute.isSelected());
            }
            case "dialCommandVolumeFocus" -> new CommandVolumeFocus();
            case "dialCommandVolumeDevice" -> new CommandVolumeDevice(
                    rdio_device_specific.isSelected() && volumedevice.getSelectionModel().getSelectedItem() != null ? volumedevice.getSelectionModel().getSelectedItem().id() : "", cb_device_unmute.isSelected());
            case "dialCommandObs" -> new CommandObsSetSourceVolume(obsAudioSources.getSelectionModel().getSelectedItem());
            case "dialCommandVoiceMeeter" -> {
                if (voicemeeterTabPaneDial.getSelectionModel().getSelectedIndex() == 0) {
                    yield new CommandVoiceMeeterBasic(voicemeeterBasicDialIO.getValue(), voicemeeterBasicDialIndex.getValue() - 1, voicemeeterBasicDial.getValue());
                }
                if (voicemeeterTabPaneDial.getSelectionModel().getSelectedIndex() == 1) {
                    if (voicemeeterDialType.getValue() == null) {
                        showError("Must Select a Control Type");
                        yield NOOP;
                    }
                    yield new CommandVoiceMeeterAdvanced(voicemeeterDialParameter.getText(), voicemeeterDialType.getValue());
                }
                yield NOOP;
            }
            case "dialCommandBrightness" -> new CommandBrightness();
            default -> NOOP;
        };
    }

    private void showError(String error) {
        var a = new Alert(AlertType.ERROR, error);
        a.initOwner(stage);
        a.show();
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
        appVolumeController.setPickType(PickProcessesController.PickType.soundSource);

        if (analogType != null)
            mainTabPane.getTabs().get(1).setText(analogType);
        if (!hasButton)
            mainTabPane.getTabs().remove(0);
        Util.adjustTabs(dialTabPane, 150, 30);
        if (obs.isConnected()) {
            var sourcesWithAudio = obs.getSourcesWithAudio();
            obsAudioSources.getItems().addAll(sourcesWithAudio);
        } else {
            if (volData instanceof CommandObsSetSourceVolume ssv) {
                obsAudioSources.getItems().add(ssv.getSourceName());
            } else {
                fxHelper.removeTabById(dialTabPane, "dialCommandObs");
            }
        }
        voicemeeterDialType.getItems().addAll(DialControlMode.values());
        if (voiceMeeter.login()) {
            voicemeeterBasicDialIO.getItems().addAll(ControlType.values());
            voicemeeterBasicDialIO.valueProperty().addListener((o, oldVal, newVal) -> {
                if (newVal == null) {
                    Util.clearAndSetNull(voicemeeterBasicDialIndex);
                    return;
                }
                Util.changeItemsTo(voicemeeterBasicDialIndex, Util.numToList(voiceMeeter.getNum(newVal)), true);
            });
            voicemeeterBasicDialIndex.valueProperty().addListener((o, oldVal, newVal) -> {
                if (newVal == null) {
                    Util.clearAndSetNull(voicemeeterBasicDial);
                    return;
                }
                Util.changeItemsTo(voicemeeterBasicDial,
                        voiceMeeter.getDialTypes(voicemeeterBasicDialIO.getValue(), voicemeeterBasicDialIndex.getValue() - 1));
            });
            voicemeeterBasicDialIO.getSelectionModel().selectFirst();
            voicemeeterBasicDialIndex.getSelectionModel().selectFirst();
        } else {
            if (volData instanceof CommandVoiceMeeter) {
                if (volData instanceof CommandVoiceMeeterBasic vmb) {
                    voicemeeterBasicDialIO.getItems().add(vmb.getCt());
                    voicemeeterBasicDialIndex.getItems().add(vmb.getIndex() + 1);
                    voicemeeterBasicDial.getItems().add(vmb.getDt());
                }
            } else {
                fxHelper.removeTabById(dialTabPane, "dialCommandVoiceMeeter");
            }
        }
        allSoundDevices = sndCtrl.getDevices();
        var outputDevices = allSoundDevices.stream().filter(AudioDevice::isOutput).toList();
        volumedevice.getItems().addAll(allSoundDevices);
        app_vol_output_device.getItems().addAll(outputDevices);
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
        onRadioButton(null);
        try {
            initFields();
        } catch (Exception e) {
            log.error("Unable to init fields", e);
        }
        onRadioButton(null);
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

    private @Nullable AudioDevice getSoundDeviceById(String id) {
        return StreamEx.of(allSoundDevices).findFirst(sd -> sd.id().equals(id)).orElse(null);
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
        dialInitializers.put(CommandVolumeProcess.class, (CommandVolumeProcess cmd) -> {
            appVolumeController.setSelection(cmd.getProcessName());
            cb_app_unmute.setSelected(cmd.isUnMuteOnVolumeChange());

            if (StringUtils.equals(cmd.getDevice(), "*")) {
                rdio_app_output_all.setSelected(true);
            } else if (StringUtils.isNotBlank(cmd.getDevice())) {
                rdio_app_output_specific.setSelected(true);
                app_vol_output_device.setValue(getSoundDeviceById(cmd.getDevice()));
            } else {
                rdio_app_output_default.setSelected(true);
            }
        });
        dialInitializers.put(CommandVolumeFocus.class, (CommandVolumeFocus cmd) -> log.trace("Focus volume does not have anything to setup"));
        dialInitializers.put(CommandVolumeDevice.class, (CommandVolumeDevice cmd) -> {
            if (StringUtils.isNotBlank(cmd.getDeviceId())) {
                rdio_device_specific.setSelected(true);
                volumedevice.setValue(getSoundDeviceById(cmd.getDeviceId()));
            } else {
                rdio_device_default.setSelected(true);
            }
            cb_device_unmute.setSelected(cmd.isUnMuteOnVolumeChange());
        });
        dialInitializers.put(CommandObsSetSourceVolume.class, (CommandObsSetSourceVolume cmd) -> obsAudioSources.getSelectionModel().select(cmd.getSourceName()));
        dialInitializers.put(CommandVoiceMeeterBasic.class, (CommandVoiceMeeterBasic cmd) -> {
            voicemeeterTabPaneDial.getSelectionModel().select(0);
            voicemeeterBasicDialIO.setValue(cmd.getCt());
            voicemeeterBasicDialIndex.setValue(cmd.getIndex() + 1);
            voicemeeterBasicDial.setValue(cmd.getDt());
        });
        dialInitializers.put(CommandVoiceMeeterAdvanced.class, (CommandVoiceMeeterAdvanced cmd) -> {
            voicemeeterTabPaneDial.getSelectionModel().select(1);
            voicemeeterDialParameter.setText(cmd.getFullParam());
            voicemeeterDialType.setValue(cmd.getCt());
        });
        dialInitializers.put(CommandBrightness.class, (CommandBrightness cmd) -> {
        });

        return dialInitializers;
    }

    @FXML
    private void onRadioButton(@Nullable ActionEvent event) {
        volumedevice.setDisable(!rdio_device_specific.isSelected());
        app_vol_output_device.setDisable(!rdio_app_output_specific.isSelected());
    }
}
