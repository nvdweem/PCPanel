package com.getpcpanel.ui;

import static com.getpcpanel.commands.command.CommandNoOp.NOOP;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandEndProgram;
import com.getpcpanel.commands.command.CommandKeystroke;
import com.getpcpanel.commands.command.CommandMedia;
import com.getpcpanel.commands.command.CommandNoOp;
import com.getpcpanel.commands.command.CommandObsMuteSource;
import com.getpcpanel.commands.command.CommandObsSetScene;
import com.getpcpanel.commands.command.CommandProfile;
import com.getpcpanel.commands.command.CommandShortcut;
import com.getpcpanel.commands.command.CommandVoiceMeeter;
import com.getpcpanel.commands.command.CommandVoiceMeeterAdvancedButton;
import com.getpcpanel.commands.command.CommandVoiceMeeterBasicButton;
import com.getpcpanel.commands.command.CommandVolumeApplicationDeviceToggle;
import com.getpcpanel.commands.command.CommandVolumeDefaultDevice;
import com.getpcpanel.commands.command.CommandVolumeDefaultDeviceAdvanced;
import com.getpcpanel.commands.command.CommandVolumeDefaultDeviceToggle;
import com.getpcpanel.commands.command.CommandVolumeDefaultDeviceToggleAdvanced;
import com.getpcpanel.commands.command.CommandVolumeDeviceMute;
import com.getpcpanel.commands.command.CommandVolumeFocusMute;
import com.getpcpanel.commands.command.CommandVolumeProcessMute;
import com.getpcpanel.cpp.AudioDevice;
import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.cpp.MuteType;
import com.getpcpanel.obs.OBS;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.spring.OsHelper;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.util.Util;
import com.getpcpanel.voicemeeter.Voicemeeter;

import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
public class MacroButtonController {
    private final OsHelper osHelper;
    private final FxHelper fxHelper;
    private final OBS obs;
    private final Voicemeeter voiceMeeter;
    private final ISndCtrl sndCtrl;

    private Collection<AudioDevice> allSoundDevices;
    private Stage stage;
    private Command buttonData;
    private DeviceSave deviceSave;
    private Profile profile;

    @FXML private AdvancedDevices applicationDeviceDevicesController;
    @FXML private AdvancedDevices defaultDeviceAdvancedController;
    @FXML private AdvancedDevices defaultDeviceToggleAdvancedController;
    @FXML private Button findAppEndProcess;
    @FXML private ChoiceBox<AudioDevice> muteSoundDevice;
    @FXML private ChoiceBox<AudioDevice> sounddevices;
    @FXML private ChoiceBox<Integer> voicemeeterBasicButtonIndex;
    @FXML private ChoiceBox<Profile> profileDropdown;
    @FXML private ChoiceBox<String> obsSetScene;
    @FXML private ChoiceBox<String> obsSourceToMute;
    @FXML private ChoiceBox<Voicemeeter.ButtonControlMode> voicemeeterButtonType;
    @FXML private ChoiceBox<Voicemeeter.ButtonType> voicemeeterBasicButton;
    @FXML private ChoiceBox<Voicemeeter.ControlType> voicemeeterBasicButtonIO;
    @FXML private ListView<AudioDevice> soundDeviceSource;
    @FXML private ListView<AudioDevice> soundDevices2;
    @FXML private Pane obsPaneMuteSource;
    @FXML private Pane obsPaneSetScene;
    @FXML private PickProcessesController appMuteController;
    @FXML private PickProcessesController applicationDeviceProcessesController;
    @FXML private RadioButton obsMuteMute;
    @FXML private RadioButton obsMuteToggle;
    @FXML private RadioButton obsMuteUnmute;
    @FXML private RadioButton obs_rdio_MuteSource;
    @FXML private RadioButton obs_rdio_SetScene;
    @FXML private RadioButton rdioApplicationDeviceFocus;
    @FXML private RadioButton rdioApplicationDeviceSpecific;
    @FXML private RadioButton rdioEndFocusedProgram;
    @FXML private RadioButton rdioEndSpecificProgram;
    @FXML private RadioButton rdio_focus_mute_mute;
    @FXML private RadioButton rdio_focus_mute_toggle;
    @FXML private RadioButton rdio_focus_mute_unmute;
    @FXML private RadioButton rdio_muteDevice_Default;
    @FXML private RadioButton rdio_muteDevice_Specific;
    @FXML private RadioButton rdio_muteDevice_mute;
    @FXML private RadioButton rdio_muteDevice_toggle;
    @FXML private RadioButton rdio_muteDevice_unmute;
    @FXML private RadioButton rdio_mute_mute;
    @FXML private RadioButton rdio_mute_toggle;
    @FXML private RadioButton rdio_mute_unmute;
    @FXML private TabPane buttonTabPane;
    @FXML private TabPane voicemeeterTabPaneButton;
    @FXML private TextField endProcessField;
    @FXML private TextField keystrokeField;
    @FXML private TextField shortcutField;
    @FXML private TextField voicemeeterButtonParameter;
    @FXML private ToggleGroup mediagroup;
    @FXML public CheckBox cmdMediaSpotify;

    private boolean k_alt;
    private boolean k_shift;
    private boolean k_win;
    private boolean k_ctrl;

    public TabPane getRoot() {
        return buttonTabPane;
    }

    public void initController(Stage stage, boolean hasButton, DeviceSave deviceSave, Profile profile, int dialNum) {
        this.stage = stage;
        this.profile = profile;
        this.deviceSave = deviceSave;
        if (hasButton) {
            buttonData = profile.getButtonData(dialNum);
        }

        postInit();
    }

    private void postInit() {
        appMuteController.setPickType(PickProcessesController.PickType.soundSource);
        defaultDeviceToggleAdvancedController.setAllowRemove(true);
        defaultDeviceAdvancedController.add();
        applicationDeviceProcessesController.setPickType(PickProcessesController.PickType.soundSource);
        applicationDeviceDevicesController.setAllowRemove(true);
        applicationDeviceDevicesController.setOnlyMedia(true);

        var toRemove = StreamEx.of(buttonTabPane.getTabs()).remove(osHelper::isSupported).toSet();
        buttonTabPane.getTabs().removeAll(toRemove);
        Util.adjustTabs(buttonTabPane, 150, 30);

        if (obs.isConnected()) {
            var sourcesWithAudio = obs.getSourcesWithAudio();
            var scenes = obs.getScenes();
            obsSourceToMute.getItems().addAll(sourcesWithAudio);
            obsSetScene.getItems().addAll(scenes);
        } else {
            if (buttonData instanceof CommandObsMuteSource ms) {
                obsSourceToMute.getItems().add(ms.getSource());
            } else if (buttonData instanceof CommandObsSetScene ss) {
                obsSetScene.getItems().add(ss.getScene());
            } else {
                fxHelper.removeTabById(buttonTabPane, "btnCommandObs");
            }
        }
        voicemeeterButtonType.getItems().addAll(Voicemeeter.ButtonControlMode.values());
        if (voiceMeeter.login()) {
            voicemeeterBasicButtonIO.getItems().addAll(Voicemeeter.ControlType.values());
            voicemeeterBasicButtonIO.valueProperty().addListener((o, oldVal, newVal) -> {
                if (newVal == null) {
                    Util.clearAndSetNull(voicemeeterBasicButtonIndex);
                    return;
                }
                Util.changeItemsTo(voicemeeterBasicButtonIndex, Util.numToList(voiceMeeter.getNum(newVal)), true);
            });
            voicemeeterBasicButtonIndex.valueProperty().addListener((o, oldVal, newVal) -> {
                if (newVal == null) {
                    Util.clearAndSetNull(voicemeeterBasicButton);
                    return;
                }
                Util.changeItemsTo(voicemeeterBasicButton,
                        voiceMeeter.getButtonTypes(voicemeeterBasicButtonIO.getValue(), voicemeeterBasicButtonIndex.getValue() - 1));
            });
            voicemeeterBasicButtonIO.getSelectionModel().selectFirst();
            voicemeeterBasicButtonIndex.getSelectionModel().selectFirst();
        } else {
            if (buttonData instanceof CommandVoiceMeeter) {
                if (buttonData instanceof CommandVoiceMeeterBasicButton vmb) {
                    voicemeeterBasicButtonIO.getItems().add(vmb.getCt());
                    voicemeeterBasicButtonIndex.getItems().add(vmb.getIndex() + 1);
                    voicemeeterBasicButton.getItems().add(vmb.getBt());
                }
            } else {
                fxHelper.removeTabById(buttonTabPane, "btnCommandVoiceMeeter");
            }
        }
        var curProfile = profile.getName();
        StreamEx.of(deviceSave.getProfiles()).removeBy(Profile::getName, curProfile).toListAndThen(profileDropdown.getItems()::addAll);
        allSoundDevices = sndCtrl.getDevices();
        muteSoundDevice.getItems().addAll(allSoundDevices);
        sounddevices.getItems().addAll(allSoundDevices);
        soundDeviceSource.getItems().addAll(allSoundDevices);
        initDeviceToggleEvents();
        soundDevices2.setCellFactory(new SoundDeviceImportFactory(soundDevices2));
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
        onRadioButton(null);
        try {
            initButtonFields();
        } catch (Exception e) {
            log.error("Unable to init fields", e);
        }
        onRadioButton(null);
    }

    private void initButtonFields() {
        if (buttonData == null || buttonData.equals(NOOP))
            return;
        fxHelper.selectTabById(buttonTabPane, "btn" + buttonData.getClass().getSimpleName());
        fxHelper.selectTabById(buttonTabPane, "btn" + buttonData.getClass().getSuperclass().getSimpleName());

        //noinspection unchecked,rawtypes
        ((Consumer) getButtonInitializer().getOrDefault(buttonData.getClass(), x -> log.error("No initializer for {}", x))).accept(buttonData); // Yuck :(
    }

    public Command determineButtonCommand(String buttonType) {
        return switch (buttonType) {
            case "btnCommandKeystroke" -> new CommandKeystroke(keystrokeField.getText());
            case "btnCommandShortcut" -> new CommandShortcut(shortcutField.getText());
            case "btnCommandMedia" -> new CommandMedia(CommandMedia.VolumeButton.valueOf(((RadioButton) mediagroup.getSelectedToggle()).getId()), cmdMediaSpotify.isSelected());
            case "btnCommandEndProgram" -> new CommandEndProgram(rdioEndSpecificProgram.isSelected(), endProcessField.getText());
            case "btnCommandVolumeDefaultDevice" -> sounddevices.getValue() == null ? NOOP : new CommandVolumeDefaultDevice(sounddevices.getValue().id());
            case "btnCommandVolumeDefaultDeviceToggle" -> new CommandVolumeDefaultDeviceToggle(soundDevices2.getItems().stream().map(AudioDevice::id).toList());
            case "btnCommandVolumeDefaultDeviceToggleAdvanced" -> new CommandVolumeDefaultDeviceToggleAdvanced(defaultDeviceToggleAdvancedController.getEntries());
            case "btnCommandVolumeProcessMute" -> new CommandVolumeProcessMute(new HashSet<>(appMuteController.getSelection()),
                    rdio_mute_unmute.isSelected() ? MuteType.unmute : rdio_mute_mute.isSelected() ? MuteType.mute : MuteType.toggle);
            case "btnCommandVolumeFocusMute" -> new CommandVolumeFocusMute(rdio_mute_unmute.isSelected() ? MuteType.unmute : rdio_mute_mute.isSelected() ? MuteType.mute : MuteType.toggle);
            case "btnCommandVolumeDeviceMute" -> {
                var device = rdio_muteDevice_Default.isSelected() || muteSoundDevice.getValue() == null ? "" : muteSoundDevice.getValue().id();
                yield new CommandVolumeDeviceMute(device, rdio_muteDevice_unmute.isSelected() ? MuteType.unmute : rdio_muteDevice_mute.isSelected() ? MuteType.mute : MuteType.toggle);
            }
            case "btnCommandVolumeDefaultDeviceAdvanced" -> {
                var entry = defaultDeviceAdvancedController.getEntries().get(0);
                yield new CommandVolumeDefaultDeviceAdvanced(entry.name(), entry.mediaPlayback(), entry.mediaRecord(), entry.communicationPlayback(), entry.communicationRecord());
            }
            case "btnCommandVolumeApplicationDeviceToggle" -> {
                var followFocus = rdioApplicationDeviceFocus.isSelected();
                var processes = followFocus ? List.<String>of() : applicationDeviceProcessesController.getSelection();
                yield new CommandVolumeApplicationDeviceToggle(processes, followFocus, applicationDeviceDevicesController.getEntries());
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

    @FXML
    private void clearKeystroke(ActionEvent event) {
        keystrokeField.setText("");
        k_alt = false;
        k_shift = false;
        k_win = false;
        k_ctrl = false;
    }

    @FXML
    private void scFile(ActionEvent event) {
        UIHelper.showFilePicker("Pick file", shortcutField);
    }

    @FXML
    private void onRadioButton(@Nullable ActionEvent event) {
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

    public void addApplicationDevice(ActionEvent ignored) {
        applicationDeviceDevicesController.add();
    }

    public void addDefaultDeviceToggleAdvanced(ActionEvent ignored) {
        defaultDeviceToggleAdvancedController.add();
    }

    private void initDeviceToggleEvents() {
        var sourceRenderer = new SoundDeviceExportFactory(soundDeviceSource);
        disableDeviceToggleOtherTypes(sourceRenderer);
        soundDeviceSource.setCellFactory(sourceRenderer);
        soundDeviceSource.getItems().addListener((ListChangeListener<AudioDevice>) change -> {
            disableDeviceToggleOtherTypes(sourceRenderer);
        });
    }

    private void disableDeviceToggleOtherTypes(SoundDeviceExportFactory sourceRenderer) {
        if (!soundDevices2.getItems().isEmpty()) {
            var df = soundDevices2.getItems().get(0).dataflow();
            sourceRenderer.setEnabledFlavor(df);
        } else {
            sourceRenderer.setEnabledFlavor(null);
        }
        soundDeviceSource.refresh();
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
        buttonInitializers.put(CommandMedia.class, (CommandMedia cmd) -> {
            mediagroup.getToggles().get(switch (cmd.getButton()) {
                        case playPause -> 0;
                        case stop -> 1;
                        case prev -> 2;
                        case next -> 3;
                        case mute -> 4;
                    }
            ).setSelected(true);
            cmdMediaSpotify.setSelected(cmd.isSpotify());
        });
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
        buttonInitializers.put(CommandVolumeDefaultDeviceToggleAdvanced.class, (CommandVolumeDefaultDeviceToggleAdvanced cmd) -> cmd.getDevices().forEach(defaultDeviceToggleAdvancedController::add));
        buttonInitializers.put(CommandVolumeApplicationDeviceToggle.class, (CommandVolumeApplicationDeviceToggle cmd) -> {
            rdioApplicationDeviceSpecific.setSelected(!cmd.isFollowFocus());
            rdioApplicationDeviceFocus.setSelected(cmd.isFollowFocus());
            applicationDeviceProcessesController.setSelection(cmd.getProcesses());
            cmd.getDevices().forEach(applicationDeviceDevicesController::add);
        });
        buttonInitializers.put(CommandVolumeProcessMute.class, (CommandVolumeProcessMute cmd) -> {
            appMuteController.setSelection(cmd.getProcessName());
            switch (cmd.getMuteType()) {
                case unmute -> rdio_mute_unmute.setSelected(true);
                case mute -> rdio_mute_mute.setSelected(true);
                case toggle -> rdio_mute_toggle.setSelected(true);
            }
        });
        buttonInitializers.put(CommandVolumeFocusMute.class, (CommandVolumeFocusMute cmd) -> {
            switch (cmd.getMuteType()) {
                case unmute -> rdio_focus_mute_unmute.setSelected(true);
                case mute -> rdio_focus_mute_mute.setSelected(true);
                case toggle -> rdio_focus_mute_toggle.setSelected(true);
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
        buttonInitializers.put(CommandVolumeDefaultDeviceAdvanced.class,
                (CommandVolumeDefaultDeviceAdvanced cmd) -> defaultDeviceAdvancedController.set(cmd.getName(), cmd.getMediaPb(), cmd.getMediaRec(), cmd.getCommunicationPb(), cmd.getCommunicationRec()));
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
        buttonInitializers.put(CommandProfile.class, (CommandProfile cmd) -> deviceSave.getProfile(cmd.getProfile()).ifPresent(profile -> profileDropdown.setValue(profile)));

        return buttonInitializers;
    }

    private @Nullable AudioDevice getSoundDeviceById(String id) {
        return StreamEx.of(allSoundDevices).findFirst(sd -> sd.id().equals(id)).orElse(null);
    }
}
