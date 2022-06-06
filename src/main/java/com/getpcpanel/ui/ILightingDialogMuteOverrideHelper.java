package com.getpcpanel.ui;

import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.ui.colorpicker.ColorDialog;

import javafx.geometry.Insets;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import one.util.streamex.StreamEx;

public interface ILightingDialogMuteOverrideHelper {
    record OverrideTarget(CheckBox[] cb, ColorDialog[] cd) {
    }

    enum OverrideTargetType {
        KNOB, SLIDER, SLIDER_LABEL
    }

    default CheckBox[] getMuteOverrideCheckboxesKnobs() {
        return new CheckBox[0];
    }

    default ColorDialog[] getMuteOverrideColorsKnobs() {
        return new ColorDialog[0];
    }

    default CheckBox[] getMuteOverrideCheckboxesSliders() {
        return new CheckBox[0];
    }

    default ColorDialog[] getMuteOverrideColorsSliders() {
        return new ColorDialog[0];
    }

    default CheckBox[] getMuteOverrideCheckboxesSliderLabels() {
        return new CheckBox[0];
    }

    default ColorDialog[] getMuteOverrideColorsSliderLabels() {
        return new ColorDialog[0];
    }

    default TabPane tabWithMuteOverride(ProLightingDialog.OverrideTargetType ott, int button, TabPane tab) {
        var target = getOverrideTarget(ott);
        var vBox = new VBox();
        target.cd()[button] = new ColorDialog();
        target.cb()[button] = new CheckBox("Enable mute override");
        var label = new Label(
                "Mute override looks at the action on the dial/slider, not the button. If a dial controls the volume of a device or process, "
                        + "the mute override will set the color when that device or process is muted. If the button action triggers mute but the slider/dial "
                        + "does not control that device/process, mute override will do nothing.");
        var insets = new Insets(15, 15, 0, 15);
        label.setPadding(insets);
        target.cb()[button].setPadding(insets);
        label.setWrapText(true);
        vBox.getChildren().addAll(
                label,
                target.cb()[button],
                target.cd()[button]
        );
        var muteTab = new Tab("Mute override", vBox);
        var originalTab = new Tab("Color", tab);

        var result = new TabPane(originalTab, muteTab);
        result.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
        return result;
    }

    default CheckBox[] allOverrideCheckboxes() {
        return StreamEx.of(OverrideTargetType.values()).flatMap(t -> StreamEx.of(getOverrideTarget(t).cb())).toArray(CheckBox[]::new);
    }

    default ColorDialog[] allOverrideColors() {
        return StreamEx.of(OverrideTargetType.values()).flatMap(t -> StreamEx.of(getOverrideTarget(t).cd())).toArray(ColorDialog[]::new);
    }

    default OverrideTarget getOverrideTarget(OverrideTargetType ott) {
        return switch (ott) {
            case KNOB -> new OverrideTarget(getMuteOverrideCheckboxesKnobs(), getMuteOverrideColorsKnobs());
            case SLIDER -> new OverrideTarget(getMuteOverrideCheckboxesSliders(), getMuteOverrideColorsSliders());
            case SLIDER_LABEL -> new OverrideTarget(getMuteOverrideCheckboxesSliderLabels(), getMuteOverrideColorsSliderLabels());
        };
    }

    default void setOverride(OverrideTargetType type, int typeIndex, String muteOverrideColor) {
        var target = getOverrideTarget(type);
        target.cb()[typeIndex].setSelected(StringUtils.isNotBlank(muteOverrideColor));
        target.cd()[typeIndex].setCustomColor(Color.web(StringUtils.defaultIfBlank(muteOverrideColor, "black")));
    }

    default void setOverrideSetting(OverrideTargetType type, int typeIdx, Consumer<Color> colorSetter) {
        var target = getOverrideTarget(type);
        var overrideColor = target.cb()[typeIdx].isSelected() ? target.cd()[typeIdx].getCustomColor() : null;
        colorSetter.accept(overrideColor);
    }
}
