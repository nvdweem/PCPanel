package com.getpcpanel.ui;

import static com.getpcpanel.commands.command.CommandNoOp.NOOP;

import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandEndProgram;
import com.getpcpanel.commands.command.CommandKeystroke;
import com.getpcpanel.commands.command.CommandMedia;
import com.getpcpanel.commands.command.CommandNoOp;
import com.getpcpanel.commands.command.CommandObsMuteSource;
import com.getpcpanel.commands.command.CommandObsSetScene;
import com.getpcpanel.commands.command.CommandObsSetSourceVolume;
import com.getpcpanel.commands.command.CommandProfile;
import com.getpcpanel.commands.command.CommandShortcut;
import com.getpcpanel.commands.command.CommandVoiceMeeter;
import com.getpcpanel.commands.command.CommandVoiceMeeterAdvanced;
import com.getpcpanel.commands.command.CommandVoiceMeeterAdvancedButton;
import com.getpcpanel.commands.command.CommandVoiceMeeterBasic;
import com.getpcpanel.commands.command.CommandVoiceMeeterBasicButton;
import com.getpcpanel.commands.command.CommandVolumeDefaultDevice;
import com.getpcpanel.commands.command.CommandVolumeDefaultDeviceToggle;
import com.getpcpanel.commands.command.CommandVolumeDevice;
import com.getpcpanel.commands.command.CommandVolumeDeviceMute;
import com.getpcpanel.commands.command.CommandVolumeFocus;
import com.getpcpanel.commands.command.CommandVolumeProcess;
import com.getpcpanel.commands.command.CommandVolumeProcessMute;
import com.getpcpanel.cpp.AudioDevice;
import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.cpp.MuteType;
import com.getpcpanel.device.Device;
import com.getpcpanel.obs.OBS;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.KnobSetting;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.util.Util;
import com.getpcpanel.voicemeeter.Voicemeeter;
import com.getpcpanel.voicemeeter.Voicemeeter.ButtonControlMode;
import com.getpcpanel.voicemeeter.Voicemeeter.ButtonType;
import com.getpcpanel.voicemeeter.Voicemeeter.ControlType;
import com.getpcpanel.voicemeeter.Voicemeeter.DialControlMode;
import com.getpcpanel.voicemeeter.Voicemeeter.DialType;

import javafx.application.Application;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
public class BasicMacro extends Application implements Initializable {
    private final FxHelper fxHelper;
    private final SaveService saveService;
    private final OBS obs;
    private final Voicemeeter voiceMeeter;
    private final ISndCtrl sndCtrl;

    @FXML private Pane topPane;
    @FXML private TabPane mainTabPane;
    @FXML private TabPane buttonTabPane;
    @FXML private TabPane dialTabPane;
    @FXML private TextField keystrokeField;
    @FXML private TextField shortcutField;
    @FXML private Button scFileButton;
    @FXML private ToggleGroup mediagroup;
    @FXML private TextField endProcessField;
    @FXML private RadioButton rdioEndFocusedProgram;
    @FXML private RadioButton rdioEndSpecificProgram;
    @FXML private Button findAppEndProcess;
    @FXML private ChoiceBox<AudioDevice> sounddevices;
    @FXML private ListView<AudioDevice> soundDeviceSource;
    @FXML private ListView<AudioDevice> soundDevices2;
    @FXML private RadioButton rdio_mute_toggle;
    @FXML private RadioButton rdio_mute_mute;
    @FXML private RadioButton rdio_mute_unmute;
    @FXML private ChoiceBox<AudioDevice> muteSoundDevice;
    @FXML private RadioButton rdio_muteDevice_toggle;
    @FXML private RadioButton rdio_muteDevice_mute;
    @FXML private RadioButton rdio_muteDevice_unmute;
    @FXML private RadioButton rdio_muteDevice_Default;
    @FXML private RadioButton rdio_muteDevice_Specific;
    @FXML private RadioButton obs_rdio_SetScene;
    @FXML private RadioButton obs_rdio_MuteSource;
    @FXML private Pane obsPaneSetScene;
    @FXML private Pane obsPaneMuteSource;
    @FXML private ChoiceBox<String> obsSetScene;
    @FXML private ChoiceBox<String> obsSourceToMute;
    @FXML private RadioButton obsMuteToggle;
    @FXML private RadioButton obsMuteMute;
    @FXML private RadioButton obsMuteUnmute;
    @FXML private TabPane voicemeeterTabPaneButton;
    @FXML private ChoiceBox<ControlType> voicemeeterBasicDialIO;
    @FXML private ChoiceBox<Integer> voicemeeterBasicDialIndex;
    @FXML private ChoiceBox<DialType> voicemeeterBasicDial;
    @FXML private TextField voicemeeterButtonParameter;
    @FXML private ChoiceBox<ButtonControlMode> voicemeeterButtonType;
    @FXML private ChoiceBox<Profile> profileDropdown;
    @FXML private PickProcessesController appVolumeController;
    @FXML private PickProcessesController appMuteController;
    @FXML private CheckBox cb_app_unmute;
    @FXML private RadioButton rdio_app_output_specific;
    @FXML private RadioButton rdio_app_output_default;
    @FXML private RadioButton rdio_app_output_all;
    @FXML private ChoiceBox<AudioDevice> app_vol_output_device;
    @FXML private CheckBox cb_device_unmute;
    @FXML private RadioButton rdio_device_default;
    @FXML private RadioButton rdio_device_specific;
    @FXML private ChoiceBox<AudioDevice> volumedevice;
    @FXML private ChoiceBox<String> obsAudioSources;
    @FXML private TabPane voicemeeterTabPaneDial;
    @FXML private ChoiceBox<ControlType> voicemeeterBasicButtonIO;
    @FXML private ChoiceBox<Integer> voicemeeterBasicButtonIndex;
    @FXML private ChoiceBox<ButtonType> voicemeeterBasicButton;
    @FXML private TextField voicemeeterDialParameter;
    @FXML private ChoiceBox<DialControlMode> voicemeeterDialType;
    @FXML private TextField trimMin;
    @FXML private TextField trimMax;
    @FXML private TextField buttonDebounceTime;
    @FXML private CheckBox logarithmic;
    private Stage stage;
    private Command buttonData;
    private Command volData;
    private final int dialNum;
    private final KnobSetting knobSetting;
    private Collection<AudioDevice> allSoundDevices;
    private boolean k_alt;
    private boolean k_shift;
    private boolean k_win;
    private boolean k_ctrl;
    private final DeviceSave deviceSave;
    private final boolean hasButton;
    private String name;
    private String analogType;

    public BasicMacro(FxHelper fxHelper, SaveService saveService, OBS obs, Voicemeeter voiceMeeter, ISndCtrl sndCtrl, Device device, int knob, boolean hasButton, String name, String analogType) {
        this(fxHelper, saveService, obs, voiceMeeter, sndCtrl, device, knob, hasButton);
        this.name = name;
        this.analogType = analogType;
    }

    public BasicMacro(FxHelper fxHelper, SaveService saveService, OBS obs, Voicemeeter voiceMeeter, ISndCtrl sndCtrl, Device device, int knob) {
        this(fxHelper, saveService, obs, voiceMeeter, sndCtrl, device, knob, true);
    }

    public BasicMacro(FxHelper fxHelper, SaveService saveService, OBS obs, Voicemeeter voiceMeeter, ISndCtrl sndCtrl, Device device, int knob, boolean hasButton) {
        this.fxHelper = fxHelper;
        this.saveService = saveService;
        this.obs = obs;
        this.voiceMeeter = voiceMeeter;
        this.sndCtrl = sndCtrl;

        deviceSave = saveService.get().getDeviceSave(device.getSerialNumber());
        if (hasButton)
            buttonData = deviceSave.getButtonData(knob);
        volData = deviceSave.getDialData(knob);
        knobSetting = deviceSave.getKnobSettings(knob);
        dialNum = knob;
        this.hasButton = hasButton;
    }

    @Override
    public void start(Stage basicmacro) throws Exception {
        stage = basicmacro;
        var loader = fxHelper.getLoader(getClass().getResource("/assets/BasicMacro.fxml"));
        loader.setController(this);
        Pane mainPane = loader.load();
        var scene = new Scene(mainPane);
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
    private void clearKeystroke(ActionEvent event) {
        keystrokeField.setText("");
        k_alt = false;
        k_shift = false;
        k_win = false;
        k_ctrl = false;
    }

    private String getSelectedTabId(TabPane tabPane) {
        var tab = tabPane.getSelectionModel().getSelectedItem();
        return (tab == null) ? null : tab.getId();
    }

    private void selectTabById(TabPane tabPane, String name) {
        var tab = getTabById(tabPane, name);
        if (tab != null)
            tabPane.getSelectionModel().select(tab);
    }

    private void removeTabById(TabPane tabPane, String name) {
        var tab = getTabById(tabPane, name);
        if (tab != null)
            tabPane.getTabs().remove(tab);
    }

    private Tab getTabById(TabPane tabPane, String name) {
        for (var tab : tabPane.getTabs()) {
            if (tab.getId().equals(name))
                return tab;
        }
        return null;
    }

    @FXML
    private void findApps(ActionEvent event) {
        TextField processTextField;
        var button = (Button) event.getSource();
        var id = button.getId();
        var afd = fxHelper.buildAppFinderDialog(stage, !"findAppEndProcess".equals(id));
        var afdStage = new Stage();
        afd.start(afdStage);
        var processNameResult = afd.getProcessName();
        if (processNameResult == null || id == null)
            return;
        if ("findAppEndProcess".equals(id)) {
            processTextField = endProcessField;
        } else {
            log.error("invalid findApp button");
            return;
        }
        processTextField.setText(processNameResult);
    }

    @FXML
    private void ok(ActionEvent event) {
        var buttonType = getSelectedTabId(buttonTabPane);
        var dialType = getSelectedTabId(dialTabPane);
        buttonData = determineButtonCommand(buttonType);
        volData = determineVolCommand(dialType);
        knobSetting.setMinTrim(NumberUtils.toInt(trimMin.getText(), 0));
        knobSetting.setMaxTrim(NumberUtils.toInt(trimMax.getText(), 100));
        knobSetting.setButtonDebounce(NumberUtils.toInt(buttonDebounceTime.getText(), 50));
        knobSetting.setLogarithmic(logarithmic.isSelected());
        if (hasButton)
            deviceSave.setButtonData(dialNum, buttonData);
        deviceSave.setDialData(dialNum, volData);
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
            default -> NOOP;
        };
    }

    private Command determineButtonCommand(String buttonType) {
        return switch (buttonType) {
            case "btnCommandKeystroke" -> new CommandKeystroke(keystrokeField.getText());
            case "btnCommandShortcut" -> new CommandShortcut(shortcutField.getText());
            case "btnCommandMedia" -> new CommandMedia(CommandMedia.VolumeButton.valueOf(((RadioButton) mediagroup.getSelectedToggle()).getId()));
            case "btnCommandEndProgram" -> new CommandEndProgram(rdioEndSpecificProgram.isSelected(), endProcessField.getText());
            case "btnCommandVolumeDefaultDevice" -> sounddevices.getValue() == null ? NOOP : new CommandVolumeDefaultDevice(sounddevices.getValue().id());
            case "btnCommandVolumeDefaultDeviceToggle" -> new CommandVolumeDefaultDeviceToggle(soundDevices2.getItems().stream().map(AudioDevice::id).toList());
            case "btnCommandVolumeProcessMute" -> new CommandVolumeProcessMute(new HashSet<>(appMuteController.getSelection()),
                    rdio_mute_unmute.isSelected() ? MuteType.unmute : rdio_mute_mute.isSelected() ? MuteType.mute : MuteType.toggle);
            case "btnCommandVolumeDeviceMute" -> {
                var device = rdio_muteDevice_Default.isSelected() || muteSoundDevice.getValue() == null ? "" : muteSoundDevice.getValue().id();
                yield new CommandVolumeDeviceMute(device, rdio_muteDevice_unmute.isSelected() ? MuteType.unmute : rdio_muteDevice_mute.isSelected() ? MuteType.mute : MuteType.toggle);
            }
            case "btnCommandObs" -> {
                if (obs_rdio_SetScene.isSelected()) {
                    yield new CommandObsSetScene(obsSetScene.getSelectionModel().getSelectedItem());
                } else if (obs_rdio_MuteSource.isSelected()) {
                    yield new CommandObsMuteSource(obsSourceToMute.getSelectionModel().getSelectedItem(),
                            obsMuteUnmute.isSelected() ? CommandObsMuteSource.MuteType.unmute : obsMuteMute.isSelected() ? CommandObsMuteSource.MuteType.mute : CommandObsMuteSource.MuteType.toggle);
                } else {
                    log.error("ERROR INVALID RADIO BUTTON IN BUTTON OBS");
                    yield NOOP;
                }
            }
            case "btnCommandVoiceMeeter" -> {
                if (voicemeeterTabPaneButton.getSelectionModel().getSelectedIndex() == 0) {
                    yield new CommandVoiceMeeterBasicButton(voicemeeterBasicButtonIO.getValue(), voicemeeterBasicButtonIndex.getValue() - 1, voicemeeterBasicButton.getValue());
                } else if (voicemeeterTabPaneButton.getSelectionModel().getSelectedIndex() == 1) {
                    if (voicemeeterButtonType.getValue() == null) {
                        yield NOOP;
                    }
                    yield new CommandVoiceMeeterAdvancedButton(voicemeeterButtonParameter.getText(), voicemeeterButtonType.getValue());
                }
                yield NOOP;
            }
            case "btnCommandProfile" -> new CommandProfile(profileDropdown.getValue() == null ? null : profileDropdown.getValue().getName());
            default -> NOOP;
        };
    }

    private void showError(String error) {
        var a = new Alert(AlertType.ERROR, error);
        a.initOwner(stage);
        a.show();
    }

    @FXML
    private void scFile(ActionEvent event) {
        UIHelper.showFilePicker("Pick file", shortcutField);
    }

    @FXML
    private void onRadioButton(ActionEvent event) {
        volumedevice.setDisable(!rdio_device_specific.isSelected());
        app_vol_output_device.setDisable(!rdio_app_output_specific.isSelected());
        if (rdioEndSpecificProgram.isSelected()) {
            endProcessField.setDisable(false);
            findAppEndProcess.setDisable(false);
        } else {
            endProcessField.setDisable(true);
            findAppEndProcess.setDisable(true);
        }
        obsPaneSetScene.setDisable(!obs_rdio_SetScene.isSelected());
        obsPaneMuteSource.setDisable(!obs_rdio_MuteSource.isSelected());
        muteSoundDevice.setDisable(!rdio_muteDevice_Specific.isSelected());
    }

    @FXML
    private void closeButtonAction() {
        stage.close();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        appMuteController.setPickType(PickProcessesController.PickType.soundSource);
        appVolumeController.setPickType(PickProcessesController.PickType.soundSource);

        if (analogType != null)
            mainTabPane.getTabs().get(1).setText(analogType);
        if (!hasButton)
            mainTabPane.getTabs().remove(0);
        Util.adjustTabs(dialTabPane, 120, 30);
        Util.adjustTabs(buttonTabPane, 120, 30);
        if (obs.isConnected()) {
            var sourcesWithAudio = obs.getSourcesWithAudio();
            var scenes = obs.getScenes();
            obsAudioSources.getItems().addAll(sourcesWithAudio);
            obsSourceToMute.getItems().addAll(sourcesWithAudio);
            obsSetScene.getItems().addAll(scenes);
        } else {
            if (volData instanceof CommandObsSetSourceVolume ssv) {
                obsAudioSources.getItems().add(ssv.getSourceName());
            } else {
                removeTabById(dialTabPane, "obs_dial");
            }

            if (buttonData instanceof CommandObsMuteSource ms) {
                obsSourceToMute.getItems().add(ms.getSource());
            } else if (buttonData instanceof CommandObsSetScene ss) {
                obsSetScene.getItems().add(ss.getScene());
            } else {
                removeTabById(buttonTabPane, "obs_button");
            }
        }
        voicemeeterDialType.getItems().addAll(DialControlMode.values());
        voicemeeterButtonType.getItems().addAll(ButtonControlMode.values());
        if (voiceMeeter.login()) {
            voicemeeterBasicButtonIO.getItems().addAll(ControlType.values());
            voicemeeterBasicDialIO.getItems().addAll(ControlType.values());
            voicemeeterBasicButtonIO.valueProperty().addListener((o, oldVal, newVal) -> {
                if (newVal == null) {
                    Util.clearAndSetNull(voicemeeterBasicButtonIndex);
                    return;
                }
                Util.changeItemsTo(voicemeeterBasicButtonIndex, Util.numToList(voiceMeeter.getNum(newVal)), true);
            });
            voicemeeterBasicDialIO.valueProperty().addListener((o, oldVal, newVal) -> {
                if (newVal == null) {
                    Util.clearAndSetNull(voicemeeterBasicDialIndex);
                    return;
                }
                Util.changeItemsTo(voicemeeterBasicDialIndex, Util.numToList(voiceMeeter.getNum(newVal)), true);
            });
            voicemeeterBasicButtonIndex.valueProperty().addListener((o, oldVal, newVal) -> {
                if (newVal == null) {
                    Util.clearAndSetNull(voicemeeterBasicButton);
                    return;
                }
                Util.changeItemsTo(voicemeeterBasicButton,
                        voiceMeeter.getButtonTypes(voicemeeterBasicButtonIO.getValue(), voicemeeterBasicButtonIndex.getValue() - 1));
            });
            voicemeeterBasicDialIndex.valueProperty().addListener((o, oldVal, newVal) -> {
                if (newVal == null) {
                    Util.clearAndSetNull(voicemeeterBasicDial);
                    return;
                }
                Util.changeItemsTo(voicemeeterBasicDial,
                        voiceMeeter.getDialTypes(voicemeeterBasicDialIO.getValue(), voicemeeterBasicDialIndex.getValue() - 1));
            });
            voicemeeterBasicButtonIO.getSelectionModel().selectFirst();
            voicemeeterBasicDialIO.getSelectionModel().selectFirst();
            voicemeeterBasicButtonIndex.getSelectionModel().selectFirst();
            voicemeeterBasicDialIndex.getSelectionModel().selectFirst();
        } else {
            if (volData instanceof CommandVoiceMeeter) {
                if (volData instanceof CommandVoiceMeeterBasic vmb) {
                    voicemeeterBasicDialIO.getItems().add(vmb.getCt());
                    voicemeeterBasicDialIndex.getItems().add(vmb.getIndex() + 1);
                    voicemeeterBasicDial.getItems().add(vmb.getDt());
                }
            } else {
                removeTabById(dialTabPane, "voicemeeter_dial");
            }

            if (buttonData instanceof CommandVoiceMeeter) {
                if (buttonData instanceof CommandVoiceMeeterBasicButton vmb) {
                    voicemeeterBasicButtonIO.getItems().add(vmb.getCt());
                    voicemeeterBasicButtonIndex.getItems().add(vmb.getIndex() + 1);
                    voicemeeterBasicButton.getItems().add(vmb.getBt());
                }
            } else {
                removeTabById(buttonTabPane, "voicemeeter_button");
            }
        }
        var curProfile = deviceSave.getCurrentProfileName();
        profileDropdown.getItems().addAll(deviceSave.getProfiles().stream().filter(c -> !c.getName().equals(curProfile)).toList());
        allSoundDevices = sndCtrl.getDevices();
        var outputDevices = allSoundDevices.stream().filter(AudioDevice::isOutput).toList();
        volumedevice.getItems().addAll(allSoundDevices);
        muteSoundDevice.getItems().addAll(allSoundDevices);
        sounddevices.getItems().addAll(allSoundDevices);
        soundDeviceSource.getItems().addAll(allSoundDevices);
        soundDeviceSource.setCellFactory(new SoundDeviceExportFactory(soundDeviceSource));
        soundDevices2.getItems().addListener((ListChangeListener<AudioDevice>) change -> {
            if (soundDeviceSource.getItems().stream().anyMatch(c -> soundDevices2.getItems().contains(c)))
                soundDeviceSource.getItems().removeAll(soundDevices2.getItems());
        });
        soundDeviceSource.getItems().addListener((ListChangeListener<AudioDevice>) change -> {
            if (soundDevices2.getItems().stream().anyMatch(c -> soundDeviceSource.getItems().contains(c)))
                soundDevices2.getItems().removeAll(soundDeviceSource.getItems());
        });
        soundDevices2.setCellFactory(new SoundDeviceImportFactory(soundDevices2));
        app_vol_output_device.getItems().addAll(outputDevices);
        keystrokeField.setOnKeyPressed(event -> {
            var code = event.getCode();
            if (code == KeyCode.ALT) {
                k_alt = true;
            } else if (code == KeyCode.SHIFT) {
                k_shift = true;
            } else if (code == KeyCode.WINDOWS) {
                k_win = true;
            } else if (code == KeyCode.CONTROL) {
                k_ctrl = true;
            } else if (!k_alt && !k_shift && !k_win && !k_ctrl) {
                if (code.name().startsWith("DIGIT")) {
                    keystrokeField.setText(code.name().substring(5));
                } else {
                    keystrokeField.setText(code.name());
                }
            } else {
                var str = new StringBuilder();
                var bools = new boolean[] { k_ctrl, k_shift, k_alt, k_win };
                var keys = new String[] { "ctrl", "shift", "alt", "windows" };
                for (var i = 0; i < 4; i++) {
                    if (bools[i])
                        str.append(keys[i]).append(" + ");
                }
                if (code.name().startsWith("DIGIT")) {
                    str.append(code.name().substring(5));
                } else {
                    str.append(code.name());
                }
                keystrokeField.setText(str.toString());
            }
        });
        keystrokeField.setOnKeyReleased(event -> {
            var code = event.getCode();
            if (code == KeyCode.ALT) {
                k_alt = false;
            } else if (code == KeyCode.SHIFT) {
                k_shift = false;
            } else if (code == KeyCode.WINDOWS) {
                k_win = false;
            } else if (code == KeyCode.CONTROL) {
                k_ctrl = false;
            }
        });
        trimMin.textProperty().addListener((observable, oldValue, newValue) -> trimMinMax(oldValue, newValue, trimMin));
        trimMax.textProperty().addListener((observable, oldValue, newValue) -> trimMinMax(oldValue, newValue, trimMax));
        buttonDebounceTime.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*") || newValue.contains("-") || StringUtils.isBlank(newValue)) {
                buttonDebounceTime.setText(newValue.replace("-", "").replaceAll("[^\\d]", ""));
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
        if (!newValue.matches("\\d*") || newValue.contains("-") || StringUtils.isBlank(newValue)) {
            trimMin.setText(newValue.replace("-", "").replaceAll("[^\\d]", ""));
        } else {
            var num = Integer.parseInt(newValue);
            if (num < 0 || num > 100) {
                trimMin.setText(oldValue);
                return;
            }
            trimMin.setText(String.valueOf(num));
        }
    }

    private AudioDevice getSoundDeviceById(String id) {
        return allSoundDevices.stream().filter(sd -> sd.id().equals(id)).findFirst().orElse(null);
    }

    private void initFields() {
        initButtonFields();
        initDialFields();

        if (knobSetting != null) {
            trimMin.setText(String.valueOf(knobSetting.getMinTrim()));
            trimMax.setText(String.valueOf(knobSetting.getMaxTrim()));
            buttonDebounceTime.setText(String.valueOf(knobSetting.getButtonDebounce()));
            logarithmic.setSelected(knobSetting.isLogarithmic());
        }
    }

    private void initButtonFields() {
        if (!hasButton || buttonData == null || buttonData.equals(NOOP))
            return;
        selectTabById(buttonTabPane, "btn" + buttonData.getClass().getSimpleName());
        selectTabById(buttonTabPane, "btn" + buttonData.getClass().getSuperclass().getSimpleName());

        //noinspection unchecked
        ((Consumer) getButtonInitializer().getOrDefault(buttonData.getClass(), x -> log.error("No initializer for {}", x))).accept(buttonData); // Yuck :(
    }

    private void initDialFields() {
        if (volData == null || volData.equals(NOOP))
            return;
        selectTabById(dialTabPane, "dial" + volData.getClass().getSimpleName());
        selectTabById(dialTabPane, "dial" + volData.getClass().getSuperclass().getSimpleName());

        //noinspection unchecked
        ((Consumer) getDialInitializer().getOrDefault(volData.getClass(), x -> log.error("No initializer for {}", x))).accept(volData); // Yuck :(
    }

    /**
     * This should either be a visitor or a Pattern matching switch (Java 17+)
     */
    private HashMap<Class<? extends Command>, Consumer<?>> getButtonInitializer() {
        var buttonInitializers = new HashMap<Class<? extends Command>, Consumer<?>>(); // Blegh

        buttonInitializers.put(CommandNoOp.class, (CommandNoOp command) -> {
        });
        buttonInitializers.put(CommandKeystroke.class, (CommandKeystroke command) -> keystrokeField.setText(command.getKeystroke()));
        buttonInitializers.put(CommandShortcut.class, cmd -> shortcutField.setText(((CommandShortcut) cmd).getShortcut()));
        buttonInitializers.put(CommandMedia.class, cmd -> mediagroup.getToggles().get(switch (((CommandMedia) cmd).getButton()) {
                    case playPause -> 0;
                    case stop -> 1;
                    case prev -> 2;
                    case next -> 3;
                    case mute -> 4;
                }
        ).setSelected(true));

        buttonInitializers.put(CommandEndProgram.class, cmd -> {
            var endProgram = (CommandEndProgram) cmd;
            if (endProgram.isSpecific()) {
                rdioEndSpecificProgram.setSelected(true);
                endProcessField.setText(endProgram.getName());
            } else {
                rdioEndFocusedProgram.setSelected(true);
            }
        });
        buttonInitializers.put(CommandVolumeDefaultDevice.class, cmd -> sounddevices.setValue(getSoundDeviceById(((CommandVolumeDefaultDevice) cmd).getDeviceId())));
        buttonInitializers.put(CommandVolumeDefaultDeviceToggle.class, (CommandVolumeDefaultDeviceToggle cmd) -> {
            var devices = StreamEx.of(cmd.getDevices()).map(this::getSoundDeviceById).toList();
            soundDevices2.getItems().addAll(devices);
            soundDeviceSource.getItems().removeAll(devices);
        });
        buttonInitializers.put(CommandVolumeProcessMute.class, (CommandVolumeProcessMute cmd) -> {
            appMuteController.setSelection(cmd.getProcessName());
            switch (cmd.getMuteType()) {
                case unmute -> rdio_mute_unmute.setSelected(true);
                case mute -> rdio_mute_mute.setSelected(true);
                case toggle -> rdio_mute_toggle.setSelected(true);
            }
        });
        buttonInitializers.put(CommandVolumeDeviceMute.class, (CommandVolumeDeviceMute cmd) -> {
            if (StringUtils.equalsAny(StringUtils.defaultString(cmd.getDeviceId(), ""), "", "default")) {
                rdio_muteDevice_Default.setSelected(true);
            } else {
                rdio_muteDevice_Specific.setSelected(true);
                muteSoundDevice.setValue(getSoundDeviceById(cmd.getDeviceId()));
            }
            switch (cmd.getMuteType()) {
                case unmute -> rdio_muteDevice_unmute.setSelected(true);
                case mute -> rdio_muteDevice_mute.setSelected(true);
                case toggle -> rdio_muteDevice_toggle.setSelected(true);
            }
        });
        buttonInitializers.put(CommandObsSetScene.class, (CommandObsSetScene cmd) -> {
            obs_rdio_SetScene.setSelected(true);
            obsSetScene.getSelectionModel().select(cmd.getScene());
        });
        buttonInitializers.put(CommandObsMuteSource.class, (CommandObsMuteSource cmd) -> {
            obs_rdio_MuteSource.setSelected(true);
            obsSourceToMute.getSelectionModel().select(cmd.getSource());
            switch (cmd.getType()) {
                case unmute -> obsMuteUnmute.setSelected(true);
                case mute -> obsMuteMute.setSelected(true);
                case toggle -> obsMuteToggle.setSelected(true);
            }
        });
        buttonInitializers.put(CommandVoiceMeeterBasicButton.class, (CommandVoiceMeeterBasicButton cmd) -> {
            voicemeeterTabPaneButton.getSelectionModel().select(0);
            voicemeeterBasicButtonIO.setValue(cmd.getCt());
            voicemeeterBasicButtonIndex.setValue(cmd.getIndex() + 1);
            voicemeeterBasicButton.setValue(cmd.getBt());
        });
        buttonInitializers.put(CommandVoiceMeeterAdvancedButton.class, (CommandVoiceMeeterAdvancedButton cmd) -> {
            voicemeeterTabPaneButton.getSelectionModel().select(1);
            voicemeeterButtonParameter.setText(cmd.getFullParam());
            voicemeeterButtonType.setValue(cmd.getBt());
        });
        buttonInitializers.put(CommandProfile.class, (CommandProfile cmd) -> profileDropdown.setValue(deviceSave.getProfile(cmd.getProfile())));

        return buttonInitializers;
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

        return dialInitializers;
    }
}
