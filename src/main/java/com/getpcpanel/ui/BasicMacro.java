package com.getpcpanel.ui;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.device.Device;
import com.getpcpanel.obs.OBS;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.KnobSetting;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.profile.Save;
import com.getpcpanel.util.SoundAudit;
import com.getpcpanel.util.SoundDevice;
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
import javafx.fxml.FXMLLoader;
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
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class BasicMacro extends Application implements Initializable {
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
    @FXML private ChoiceBox<SoundDevice> sounddevices;
    @FXML private ListView<SoundDevice> soundDeviceSource;
    @FXML private ListView<SoundDevice> soundDevices2;
    @FXML private TextField muteAppProcessField;
    @FXML private RadioButton rdio_mute_toggle;
    @FXML private RadioButton rdio_mute_mute;
    @FXML private RadioButton rdio_mute_unmute;
    @FXML private ChoiceBox<SoundDevice> muteSoundDevice;
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
    @FXML private TextField volumeProcessField1;
    @FXML private TextField volumeProcessField2;
    @FXML private RadioButton rdio_app_output_specific;
    @FXML private RadioButton rdio_app_output_default;
    @FXML private RadioButton rdio_app_output_all;
    @FXML private ChoiceBox<SoundDevice> app_vol_output_device;
    @FXML private RadioButton rdio_device_default;
    @FXML private RadioButton rdio_device_specific;
    @FXML private ChoiceBox<SoundDevice> volumedevice;
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
    private String[] buttonData;
    private final String[] volData;
    private final int dialNum;
    private final KnobSetting knobSetting;
    private List<SoundDevice> allSoundDevices;
    private boolean k_alt;
    private boolean k_shift;
    private boolean k_win;
    private boolean k_ctrl;
    private final DeviceSave deviceSave;
    private final boolean hasButton;
    private String name;
    private String analogType;

    public BasicMacro(Device device, int knob, boolean hasButton, String name, String analogType) {
        this(device, knob, hasButton);
        this.name = name;
        this.analogType = analogType;
    }

    public BasicMacro(Device device, int knob) {
        this(device, knob, true);
    }

    public BasicMacro(Device device, int knob, boolean hasButton) {
        deviceSave = Save.getDeviceSave(device.getSerialNumber());
        if (hasButton)
            buttonData = deviceSave.buttonData[knob];
        volData = deviceSave.dialData[knob];
        knobSetting = deviceSave.getKnobSettings()[knob];
        dialNum = knob;
        this.hasButton = hasButton;
    }

    @Override
    public void start(Stage basicmacro) throws Exception {
        stage = basicmacro;
        var loader = new FXMLLoader(getClass().getResource("/assets/BasicMacro.fxml"));
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

    private String getSelectedTabName(TabPane tabPane) {
        var tab = tabPane.getSelectionModel().getSelectedItem();
        return (tab == null) ? null : tab.getId();
    }

    private void selectTabByName(TabPane tabPane, String name) {
        var tab = getTabByName(tabPane, name);
        if (tab != null)
            tabPane.getSelectionModel().select(tab);
    }

    private void removeTabByName(TabPane tabPane, String name) {
        var tab = getTabByName(tabPane, name);
        if (tab != null)
            tabPane.getTabs().remove(tab);
    }

    private Tab getTabByName(TabPane tabPane, String name) {
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
        var afd = new AppFinderDialog(stage);
        var afdStage = new Stage();
        afd.start(afdStage);
        var processNameResult = afd.getProcessName();
        if (processNameResult == null || id == null)
            return;
        switch (id) {
            case "findApp1" -> processTextField = volumeProcessField1;
            case "findApp2" -> processTextField = volumeProcessField2;
            case "findAppMute" -> processTextField = muteAppProcessField;
            case "findAppEndProcess" -> processTextField = endProcessField;
            default -> {
                log.error("invalid findApp button");
                return;
            }
        }
        processTextField.setText(processNameResult);
    }

    @FXML
    private void ok(ActionEvent event) {
        var buttonType = getSelectedTabName(buttonTabPane);
        var dialType = getSelectedTabName(dialTabPane);
        if ("keystroke".equals(buttonType)) {
            buttonData = new String[2];
            buttonData[1] = keystrokeField.getText();
        } else if ("shortcut".equals(buttonType)) {
            buttonData = new String[2];
            buttonData[1] = shortcutField.getText();
        } else if ("media".equals(buttonType)) {
            buttonData = new String[2];
            buttonData[1] = ((RadioButton) mediagroup.getSelectedToggle()).getId();
        } else if ("end_program".equals(buttonType)) {
            buttonData = new String[3];
            if (rdioEndSpecificProgram.isSelected()) {
                buttonData[1] = "specific";
                buttonData[2] = endProcessField.getText();
            } else if (rdioEndFocusedProgram.isSelected()) {
                buttonData[1] = "focused";
            }
        } else if ("sound_device".equals(buttonType)) {
            buttonData = new String[2];
            buttonData[1] = sounddevices.getValue() == null ? null : sounddevices.getValue().getId();
        } else if ("toggle_device".equals(buttonType)) {
            buttonData = new String[3];
            buttonData[1] = Util.listToPipeDelimitedString(soundDevices2.getItems().stream().map(SoundDevice::getId).collect(Collectors.toList()));
        } else if ("mute_app".equals(buttonType)) {
            buttonData = new String[3];
            buttonData[1] = muteAppProcessField.getText();
            if (rdio_mute_unmute.isSelected()) {
                buttonData[2] = "unmute";
            } else if (rdio_mute_mute.isSelected()) {
                buttonData[2] = "mute";
            } else if (rdio_mute_toggle.isSelected()) {
                buttonData[2] = "toggle";
            }
        } else if ("mute_device".equals(buttonType)) {
            buttonData = new String[3];
            if (rdio_muteDevice_Default.isSelected() || muteSoundDevice.getValue() == null) {
                buttonData[1] = "default";
            } else {
                buttonData[1] = muteSoundDevice.getValue().getId();
            }
            if (rdio_muteDevice_unmute.isSelected()) {
                buttonData[2] = "unmute";
            } else if (rdio_muteDevice_mute.isSelected()) {
                buttonData[2] = "mute";
            } else if (rdio_muteDevice_toggle.isSelected()) {
                buttonData[2] = "toggle";
            }
        } else if ("obs_button".equals(buttonType)) {
            buttonData = new String[4];
            if (obs_rdio_SetScene.isSelected()) {
                buttonData[1] = "set_scene";
                buttonData[2] = obsSetScene.getSelectionModel().getSelectedItem();
            } else if (obs_rdio_MuteSource.isSelected()) {
                buttonData[1] = "mute_source";
                buttonData[2] = obsSourceToMute.getSelectionModel().getSelectedItem();
                if (obsMuteUnmute.isSelected()) {
                    buttonData[3] = "unmute";
                } else if (obsMuteMute.isSelected()) {
                    buttonData[3] = "mute";
                } else if (obsMuteToggle.isSelected()) {
                    buttonData[3] = "toggle";
                }
            } else {
                log.error("ERROR INVALID RADIO BUTTON IN BUTTON OBS");
            }
        } else if ("voicemeeter_button".equals(buttonType)) {
            buttonData = new String[5];
            if (voicemeeterTabPaneButton.getSelectionModel().getSelectedIndex() == 0) {
                buttonData[1] = "basic";
                buttonData[2] = voicemeeterBasicButtonIO.getValue().name();
                buttonData[3] = String.valueOf(voicemeeterBasicButtonIndex.getValue() - 1);
                var bt = voicemeeterBasicButton.getValue();
                buttonData[4] = (bt == null) ? null : bt.name();
            } else if (voicemeeterTabPaneButton.getSelectionModel().getSelectedIndex() == 1) {
                if (voicemeeterButtonType.getValue() == null) {
                    showError("Must Select a Control Type");
                    return;
                }
                buttonData[1] = "advanced";
                buttonData[2] = voicemeeterButtonParameter.getText();
                buttonData[3] = voicemeeterButtonType.getValue().name();
            }
        } else if ("profile".equals(buttonType)) {
            buttonData = new String[2];
            buttonData[1] = (profileDropdown.getValue() == null) ? null : profileDropdown.getValue().getName();
        }
        if ("app_volume".equals(dialType)) {
            volData[1] = volumeProcessField1.getText();
            volData[2] = volumeProcessField2.getText();
            if (rdio_app_output_all.isSelected()) {
                volData[3] = "*";
            } else if (rdio_app_output_specific.isSelected()) {
                volData[3] = app_vol_output_device.getSelectionModel().getSelectedItem().getId();
            } else if (rdio_app_output_default.isSelected()) {
                volData[3] = "";
            }
        } else if (!"focus_volume".equals(dialType)) {
            if ("device_volume".equals(dialType)) {
                if (rdio_device_specific.isSelected() && volumedevice.getSelectionModel().getSelectedItem() != null) {
                    volData[1] = volumedevice.getSelectionModel().getSelectedItem().getId();
                } else {
                    volData[1] = "";
                }
            } else if ("obs_dial".equals(dialType)) {
                volData[1] = "mix";
                volData[2] = obsAudioSources.getSelectionModel().getSelectedItem();
            } else if ("voicemeeter_dial".equals(dialType)) {
                if (voicemeeterTabPaneDial.getSelectionModel().getSelectedIndex() == 0) {
                    volData[1] = "basic";
                    volData[2] = voicemeeterBasicDialIO.getValue().name();
                    volData[3] = String.valueOf(voicemeeterBasicDialIndex.getValue() - 1);
                    var dt = voicemeeterBasicDial.getValue();
                    volData[4] = (dt == null) ? null : dt.name();
                } else if (voicemeeterTabPaneDial.getSelectionModel().getSelectedIndex() == 1) {
                    if (voicemeeterDialType.getValue() == null) {
                        showError("Must Select a Control Type");
                        return;
                    }
                    volData[1] = "advanced";
                    volData[2] = voicemeeterDialParameter.getText();
                    volData[3] = voicemeeterDialType.getValue().name();
                }
            }
        }
        knobSetting.setMinTrim(Util.toInt(trimMin.getText(), 0));
        knobSetting.setMaxTrim(Util.toInt(trimMax.getText(), 100));
        knobSetting.setButtonDebounce(Util.toInt(buttonDebounceTime.getText(), 50));
        knobSetting.setLogarithmic(logarithmic.isSelected());
        buttonData[0] = buttonType;
        volData[0] = dialType;
        if (hasButton)
            deviceSave.buttonData[dialNum] = buttonData;
        deviceSave.dialData[dialNum] = volData;
        if (log.isDebugEnabled()) {
            log.debug("-----------------");
            for (var d : buttonData) {
                log.debug(d);
            }
            log.debug("-----------------");
        }
        Save.saveFile();
        stage.close();
    }

    private void showError(String error) {
        var a = new Alert(AlertType.ERROR, error);
        a.initOwner(stage);
        a.show();
    }

    @FXML
    private void scFile(ActionEvent event) {
        var stage = (Stage) scFileButton.getScene().getWindow();
        var fileChooser = new FileChooser();
        fileChooser.setTitle("Open File");
        var f = fileChooser.showOpenDialog(stage);
        if (f == null)
            return;
        shortcutField.setText(f.getPath());
        log.debug(f.getPath());
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
        if (analogType != null)
            mainTabPane.getTabs().get(1).setText(analogType);
        if (!hasButton)
            mainTabPane.getTabs().remove(0);
        Util.adjustTabs(dialTabPane, 120, 30);
        Util.adjustTabs(buttonTabPane, 120, 30);
        if (OBS.isConnected()) {
            var sourcesWithAudio = OBS.getSourcesWithAudio();
            var scenes = OBS.getScenes();
            obsAudioSources.getItems().addAll(sourcesWithAudio);
            obsSourceToMute.getItems().addAll(sourcesWithAudio);
            obsSetScene.getItems().addAll(scenes);
        } else {
            if (volData[0] != null && "obs_dial".equals(volData[0])) {
                if (volData[1] != null && "mix".equals(volData[1]))
                    obsAudioSources.getItems().add(volData[2]);
            } else {
                removeTabByName(dialTabPane, "obs_dial");
            }
            if (buttonData != null && buttonData.length > 0 && buttonData[0] != null && "obs_button".equals(buttonData[0])) {
                if (buttonData[1] != null)
                    if ("set_scene".equals(buttonData[1])) {
                        obsSetScene.getItems().add(buttonData[2]);
                    } else if ("mute_source".equals(buttonData[1])) {
                        obsSourceToMute.getItems().add(buttonData[2]);
                    }
            } else {
                removeTabByName(buttonTabPane, "obs_button");
            }
        }
        voicemeeterDialType.getItems().addAll(DialControlMode.values());
        voicemeeterButtonType.getItems().addAll(ButtonControlMode.values());
        if (Voicemeeter.login()) {
            voicemeeterBasicButtonIO.getItems().addAll(ControlType.values());
            voicemeeterBasicDialIO.getItems().addAll(ControlType.values());
            voicemeeterBasicButtonIO.valueProperty().addListener((o, oldVal, newVal) -> {
                if (newVal == null) {
                    Util.clearAndSetNull(voicemeeterBasicButtonIndex);
                    return;
                }
                Util.changeItemsTo(voicemeeterBasicButtonIndex, Util.numToList(Voicemeeter.getNum(newVal)), true);
            });
            voicemeeterBasicDialIO.valueProperty().addListener((o, oldVal, newVal) -> {
                if (newVal == null) {
                    Util.clearAndSetNull(voicemeeterBasicDialIndex);
                    return;
                }
                Util.changeItemsTo(voicemeeterBasicDialIndex, Util.numToList(Voicemeeter.getNum(newVal)), true);
            });
            voicemeeterBasicButtonIndex.valueProperty().addListener((o, oldVal, newVal) -> {
                if (newVal == null) {
                    Util.clearAndSetNull(voicemeeterBasicButton);
                    return;
                }
                Util.changeItemsTo(voicemeeterBasicButton,
                        Voicemeeter.getButtonTypes(voicemeeterBasicButtonIO.getValue(), voicemeeterBasicButtonIndex.getValue() - 1));
            });
            voicemeeterBasicDialIndex.valueProperty().addListener((o, oldVal, newVal) -> {
                if (newVal == null) {
                    Util.clearAndSetNull(voicemeeterBasicDial);
                    return;
                }
                Util.changeItemsTo(voicemeeterBasicDial,
                        Voicemeeter.getDialTypes(voicemeeterBasicDialIO.getValue(), voicemeeterBasicDialIndex.getValue() - 1));
            });
            voicemeeterBasicButtonIO.getSelectionModel().selectFirst();
            voicemeeterBasicDialIO.getSelectionModel().selectFirst();
            voicemeeterBasicButtonIndex.getSelectionModel().selectFirst();
            voicemeeterBasicDialIndex.getSelectionModel().selectFirst();
        } else {
            if (volData[0] != null && "voicemeeter_dial".equals(volData[0])) {
                if (volData[1] != null && "basic".equals(volData[1])) {
                    voicemeeterBasicDialIO.getItems().add(ControlType.valueOf(volData[2]));
                    voicemeeterBasicDialIndex.getItems().add(Util.toInt(volData[3], 0) + 1);
                    voicemeeterBasicDial.getItems().add(DialType.valueOf(volData[4]));
                }
            } else {
                removeTabByName(dialTabPane, "voicemeeter_dial");
            }
            if (buttonData != null && buttonData.length > 0 && buttonData[0] != null && "voicemeeter_button".equals(buttonData[0])) {
                if (buttonData[1] != null && "basic".equals(buttonData[1])) {
                    voicemeeterBasicButtonIO.getItems().add(ControlType.valueOf(buttonData[2]));
                    voicemeeterBasicButtonIndex.getItems().add(Util.toInt(buttonData[3], 0) + 1);
                    voicemeeterBasicButton.getItems().add(ButtonType.valueOf(buttonData[4]));
                }
            } else {
                removeTabByName(buttonTabPane, "voicemeeter_button");
            }
        }
        var curProfile = deviceSave.getCurrentProfileName();
        profileDropdown.getItems().addAll(deviceSave.getProfiles().stream().filter(c -> !c.getName().equals(curProfile)).toList());
        allSoundDevices = SoundAudit.getDevices();
        var outputDevices = allSoundDevices.stream().filter(SoundDevice::isOutput).toList();
        volumedevice.getItems().addAll(allSoundDevices);
        muteSoundDevice.getItems().addAll(allSoundDevices);
        sounddevices.getItems().addAll(allSoundDevices);
        soundDeviceSource.getItems().addAll(allSoundDevices);
        soundDeviceSource.setCellFactory(new SoundDeviceExportFactory(soundDeviceSource));
        soundDevices2.getItems().addListener((ListChangeListener<SoundDevice>) change -> {
            if (soundDeviceSource.getItems().stream().anyMatch(c -> soundDevices2.getItems().contains(c)))
                soundDeviceSource.getItems().removeAll(soundDevices2.getItems());
        });
        soundDeviceSource.getItems().addListener((ListChangeListener<SoundDevice>) change -> {
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

    private SoundDevice getSoundDeviceById(String id) {
        return allSoundDevices.stream().filter(sd -> sd.getId().equals(id)).findFirst().orElse(null);
    }

    private void initFields() {
        if (volData[0] == null)
            return;
        if (hasButton && buttonData != null && buttonData[0] != null) {
            var buttonType = buttonData[0];
            selectTabByName(buttonTabPane, buttonType);
            switch (buttonType) {
                case "keystroke" -> keystrokeField.setText(buttonData[1]);
                case "shortcut" -> shortcutField.setText(buttonData[1]);
                case "media" -> {
                    if ("media1".equals(buttonData[1])) {
                        mediagroup.getToggles().get(0).setSelected(true);
                    } else if ("media2".equals(buttonData[1])) {
                        mediagroup.getToggles().get(1).setSelected(true);
                    } else if ("media3".equals(buttonData[1])) {
                        mediagroup.getToggles().get(2).setSelected(true);
                    } else if ("media4".equals(buttonData[1])) {
                        mediagroup.getToggles().get(3).setSelected(true);
                    } else if ("media5".equals(buttonData[1])) {
                        mediagroup.getToggles().get(4).setSelected(true);
                    }
                }
                case "end_program" -> {
                    if ("specific".equals(buttonData[1])) {
                        rdioEndSpecificProgram.setSelected(true);
                        endProcessField.setText(buttonData[2]);
                    } else if ("focused".equals(buttonData[1])) {
                        rdioEndFocusedProgram.setSelected(true);
                    }
                }
                case "sound_device" -> sounddevices.setValue(getSoundDeviceById(buttonData[1]));
                case "toggle_device" -> {
                    var devicesStr = buttonData[1].split("\\|");
                    var devices = Arrays.stream(devicesStr).map(this::getSoundDeviceById).toList();
                    soundDevices2.getItems().addAll(devices);
                    soundDeviceSource.getItems().removeAll(devices);
                }
                case "mute_app" -> {
                    muteAppProcessField.setText(buttonData[1]);
                    if (buttonData.length >= 3 && buttonData[2] != null)
                        if ("unmute".equals(buttonData[2])) {
                            rdio_mute_unmute.setSelected(true);
                        } else if ("mute".equals(buttonData[2])) {
                            rdio_mute_mute.setSelected(true);
                        } else if ("toggle".equals(buttonData[2])) {
                            rdio_mute_toggle.setSelected(true);
                        }
                }
                case "mute_device" -> {
                    if ("default".equals(buttonData[1])) {
                        rdio_muteDevice_Default.setSelected(true);
                    } else {
                        rdio_muteDevice_Specific.setSelected(true);
                        muteSoundDevice.setValue(getSoundDeviceById(buttonData[1]));
                    }
                    if ("unmute".equals(buttonData[2])) {
                        rdio_muteDevice_unmute.setSelected(true);
                    } else if ("mute".equals(buttonData[2])) {
                        rdio_muteDevice_mute.setSelected(true);
                    } else if ("toggle".equals(buttonData[2])) {
                        rdio_muteDevice_toggle.setSelected(true);
                    }
                }
                case "obs_button" -> {
                    if ("set_scene".equals(buttonData[1])) {
                        obs_rdio_SetScene.setSelected(true);
                        obsSetScene.getSelectionModel().select(buttonData[2]);
                    } else if ("mute_source".equals(buttonData[1])) {
                        obs_rdio_MuteSource.setSelected(true);
                        obsSourceToMute.getSelectionModel().select(buttonData[2]);
                        if ("unmute".equals(buttonData[3])) {
                            obsMuteUnmute.setSelected(true);
                        } else if ("mute".equals(buttonData[3])) {
                            obsMuteMute.setSelected(true);
                        } else if ("toggle".equals(buttonData[3])) {
                            obsMuteToggle.setSelected(true);
                        }
                    }
                }
                case "voicemeeter_button" -> {
                    if ("basic".equals(buttonData[1])) {
                        voicemeeterTabPaneButton.getSelectionModel().select(0);
                        voicemeeterBasicButtonIO.setValue(ControlType.valueOf(buttonData[2]));
                        voicemeeterBasicButtonIndex.setValue(Util.toInt(buttonData[3], 0) + 1);
                        voicemeeterBasicButton.setValue(ButtonType.valueOf(buttonData[4]));
                    } else if ("advanced".equals(buttonData[1])) {
                        voicemeeterTabPaneButton.getSelectionModel().select(1);
                        voicemeeterButtonParameter.setText(buttonData[2]);
                        voicemeeterButtonType.setValue(ButtonControlMode.valueOf(buttonData[3]));
                    }
                }
                case "profile" -> profileDropdown.setValue(deviceSave.getProfile(buttonData[1]));
            }
        }
        var dialType = volData[0];
        selectTabByName(dialTabPane, dialType);
        if ("app_volume".equals(dialType)) {
            volumeProcessField1.setText(volData[1]);
            volumeProcessField2.setText(volData[2]);
            if (!Util.isNullOrEmpty(volData[3]) && "*".equals(volData[3])) {
                rdio_app_output_all.setSelected(true);
            } else if (!Util.isNullOrEmpty(volData[3]) && !volData[3].endsWith(".exe")) {
                rdio_app_output_specific.setSelected(true);
                app_vol_output_device.setValue(getSoundDeviceById(volData[3]));
            } else {
                rdio_app_output_default.setSelected(true);
            }
        } else if (!"focus_volume".equals(dialType)) {
            if ("device_volume".equals(dialType)) {
                if (volData.length >= 2 && !Util.isNullOrEmpty(volData[1])) {
                    rdio_device_specific.setSelected(true);
                    volumedevice.setValue(getSoundDeviceById(volData[1]));
                } else {
                    rdio_device_default.setSelected(true);
                }
            } else if ("obs_dial".equals(dialType)) {
                if ("mix".equals(volData[1]))
                    obsAudioSources.getSelectionModel().select(volData[2]);
            } else if ("voicemeeter_dial".equals(dialType)) {
                if ("basic".equals(volData[1])) {
                    voicemeeterTabPaneDial.getSelectionModel().select(0);
                    voicemeeterBasicDialIO.setValue(ControlType.valueOf(volData[2]));
                    voicemeeterBasicDialIndex.setValue(Util.toInt(volData[3], 0) + 1);
                    voicemeeterBasicDial.setValue(DialType.valueOf(volData[4]));
                } else if ("advanced".equals(volData[1])) {
                    voicemeeterTabPaneDial.getSelectionModel().select(1);
                    voicemeeterDialParameter.setText(volData[2]);
                    voicemeeterDialType.setValue(DialControlMode.valueOf(volData[3]));
                }
            }
        }
        trimMin.setText(String.valueOf(knobSetting.getMinTrim()));
        trimMax.setText(String.valueOf(knobSetting.getMaxTrim()));
        buttonDebounceTime.setText(String.valueOf(knobSetting.getButtonDebounce()));
        logarithmic.setSelected(knobSetting.isLogarithmic());
    }
}
