package com.getpcpanel.ui;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.MainFX;
import com.getpcpanel.cpp.AudioDevice;
import com.getpcpanel.ui.colorpicker.ColorDialog;
import com.getpcpanel.voicemeeter.Voicemeeter;
import com.getpcpanel.voicemeeter.Voicemeeter.ButtonType;
import com.getpcpanel.voicemeeter.Voicemeeter.ControlType;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;

public interface ILightingDialogMuteOverrideHelper {
    String FOLLOW_PROCESS = "Follow what is controlled by this knob/slider";

    record OverrideTarget(CheckBox[] cb, ComboBox<String>[] deviceProcess, ColorDialog[] cd) {
    }

    enum OverrideTargetType {
        KNOB, SLIDER, SLIDER_LABEL
    }

    CheckBox[] getMuteOverrideCheckboxesKnobs();

    ComboBox<String>[] getMuteOverrideComboBoxesKnobs();

    ColorDialog[] getMuteOverrideColorsKnobs();

    default CheckBox[] getMuteOverrideCheckboxesSliders() {
        return new CheckBox[0];
    }

    default ComboBox<String>[] getMuteOverrideComboBoxesSliders() {
        //noinspection unchecked
        return new ComboBox[0];
    }

    default ColorDialog[] getMuteOverrideColorsSliders() {
        return new ColorDialog[0];
    }

    default CheckBox[] getMuteOverrideCheckboxesSliderLabels() {
        return new CheckBox[0];
    }

    default ComboBox<String>[] getMuteOverrideComboBoxesSliderLabels() {
        //noinspection unchecked
        return new ComboBox[0];
    }

    default ColorDialog[] getMuteOverrideColorsSliderLabels() {
        return new ColorDialog[0];
    }

    Collection<AudioDevice> getDevices();

    @SuppressWarnings("NestedAssignment")
    default TabPane tabWithMuteOverride(ProLightingDialog.OverrideTargetType ott, int button, TabPane tab) {
        var target = getOverrideTarget(ott);
        var vBox = new VBox();
        var cd = target.cd()[button] = new ColorDialog();
        var deviceProcess = target.deviceProcess()[button] = new ComboBox<>();
        var cb = target.cb()[button] = new CheckBox("Enable mute override");
        var label = new Label(
                "Mute override looks at the action on the dial/slider, not the button. If a dial controls the volume of a device or process, "
                        + "the mute override will set the color when that device or process is muted. If the button action triggers mute but the slider/dial "
                        + "does not control that device/process, mute override will do nothing.");
        var insets = new Insets(15, 15, 0, 15);
        label.setPadding(insets);
        cb.setPadding(insets);
        label.setWrapText(true);
        vBox.getChildren().addAll(
                label,
                cb,
                setDeviceProcessOptions(deviceProcess, insets),
                cd
        );

        var muteTab = new Tab("Mute override", vBox);
        var originalTab = new Tab("Color", tab);

        var result = new TabPane(originalTab, muteTab);
        result.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
        return result;
    }

    private HBox setDeviceProcessOptions(ComboBox<String> deviceProcess, Insets insets) {
        deviceProcess.setEditable(true);
        deviceProcess.setTooltip(new Tooltip("Can be a partial device name"));

        StreamEx.of(FOLLOW_PROCESS).append(devices())
                .append(voiceMeeterOptions())
                .forEach(deviceProcess.getItems()::add);

        deviceProcess.setPrefWidth(20000);
        var label = new Label("Follow: ");
        label.setMinWidth(100);

        var deviceProcessBox = new HBox();
        deviceProcessBox.setPadding(insets);
        deviceProcessBox.setAlignment(Pos.CENTER);
        deviceProcessBox.getChildren().add(label);
        deviceProcessBox.getChildren().add(deviceProcess);
        return deviceProcessBox;
    }

    default StreamEx<String> devices() {
        return StreamEx.of(getDevices()).map(AudioDevice::name).sorted();
    }

    default StreamEx<String> voiceMeeterOptions() {
        var voiceMeeter = MainFX.getBean(Voicemeeter.class);
        var version = voiceMeeter.getVersion();
        if (!voiceMeeter.login() || version == null) {
            return StreamEx.of();
        }

        return EntryStream.of(Collections.nCopies(voiceMeeter.getNumStrips(), ControlType.STRIP)).append(EntryStream.of(Collections.nCopies(voiceMeeter.getNumBuses(), ControlType.BUS)))
                          .flatMapKeyValue((idx, ct) -> StreamEx.of(ButtonType.stateButtonsFor(ct, version)).map(sb -> "VoiceMeeter: " + ct.getDn() + " " + (idx + 1) + ", " + sb));
    }

    default CheckBox[] allOverrideCheckboxes() {
        return StreamEx.of(OverrideTargetType.values()).flatMap(t -> StreamEx.of(getOverrideTarget(t).cb())).toArray(CheckBox[]::new);
    }

    default ColorDialog[] allOverrideColors() {
        return StreamEx.of(OverrideTargetType.values()).flatMap(t -> StreamEx.of(getOverrideTarget(t).cd())).toArray(ColorDialog[]::new);
    }

    default ComboBox<?>[] allOverrideComboBoxes() {
        return StreamEx.of(OverrideTargetType.values()).flatMap(t -> StreamEx.of(getOverrideTarget(t).deviceProcess())).toArray(ComboBox[]::new);
    }

    default OverrideTarget getOverrideTarget(OverrideTargetType ott) {
        return switch (ott) {
            case KNOB -> new OverrideTarget(getMuteOverrideCheckboxesKnobs(), getMuteOverrideComboBoxesKnobs(), getMuteOverrideColorsKnobs());
            case SLIDER -> new OverrideTarget(getMuteOverrideCheckboxesSliders(), getMuteOverrideComboBoxesSliders(), getMuteOverrideColorsSliders());
            case SLIDER_LABEL -> new OverrideTarget(getMuteOverrideCheckboxesSliderLabels(), getMuteOverrideComboBoxesSliderLabels(), getMuteOverrideColorsSliderLabels());
        };
    }

    default void setOverride(OverrideTargetType type, int typeIndex, String deviceOrFollow, String muteOverrideColor) {
        var target = getOverrideTarget(type);
        target.cb()[typeIndex].setSelected(StringUtils.isNotBlank(muteOverrideColor));
        target.deviceProcess()[typeIndex].setValue(deviceOrFollow);
        target.cd()[typeIndex].setCustomColor(Color.web(StringUtils.defaultIfBlank(muteOverrideColor, "black")));
    }

    default void setOverrideSetting(OverrideTargetType type, int typeIdx, Consumer<String> deviceFollowSetter, Consumer<Color> colorSetter) {
        var target = getOverrideTarget(type);
        if (target.cb()[typeIdx].isSelected()) {
            deviceFollowSetter.accept(target.deviceProcess()[typeIdx].getValue());
            colorSetter.accept(target.cd()[typeIdx].getCustomColor());
        } else {
            deviceFollowSetter.accept(null);
            colorSetter.accept(null);
        }
    }
}
