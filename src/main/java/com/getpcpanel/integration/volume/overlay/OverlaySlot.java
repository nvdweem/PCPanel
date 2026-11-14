package com.getpcpanel.integration.volume.overlay;

import java.util.function.Function;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.profile.dto.KnobSetting;

/**
 * Which of a control's action slots the overlay shows for, and the overlay icon and name the user set for that slot.
 * The control's own {@link KnobSetting#getOverlayIcon()}/{@link KnobSetting#getOverlayName()} are the default for every
 * slot that has none of its own.
 */
enum OverlaySlot {
    TURN(KnobSetting::getTurnOverlayIcon, KnobSetting::getTurnOverlayName),
    PRESS(KnobSetting::getPressOverlayIcon, KnobSetting::getPressOverlayName),
    DOUBLE_PRESS(KnobSetting::getDblPressOverlayIcon, KnobSetting::getDblPressOverlayName),
    HOLD(KnobSetting::getHoldOverlayIcon, KnobSetting::getHoldOverlayName),
    RELEASE(KnobSetting::getReleaseOverlayIcon, KnobSetting::getReleaseOverlayName);

    private final Function<KnobSetting, String> icon;
    private final Function<KnobSetting, String> name;

    OverlaySlot(Function<KnobSetting, String> icon, Function<KnobSetting, String> name) {
        this.icon = icon;
        this.name = name;
    }

    /** The slot's own icon, else the control's default; null when neither is set. */
    @Nullable
    static String icon(@Nullable KnobSetting setting, @Nullable OverlaySlot slot) {
        return pick(setting, slot == null ? null : slot.icon, KnobSetting::getOverlayIcon);
    }

    /** The slot's own name, else the control's default; null when neither is set. */
    @Nullable
    static String name(@Nullable KnobSetting setting, @Nullable OverlaySlot slot) {
        return pick(setting, slot == null ? null : slot.name, KnobSetting::getOverlayName);
    }

    /** Whether the button slot has a name of its own, which shows the overlay for every press of it. */
    static boolean hasOwnName(@Nullable KnobSetting setting, @Nullable OverlaySlot slot) {
        return setting != null && slot != null && slot != TURN && StringUtils.isNotBlank(slot.name.apply(setting));
    }

    @Nullable
    private static String pick(@Nullable KnobSetting setting, @Nullable Function<KnobSetting, String> own, Function<KnobSetting, String> fallback) {
        if (setting == null) {
            return null;
        }
        var value = own == null ? null : own.apply(setting);
        return StringUtils.isNotBlank(value) ? value : fallback.apply(setting);
    }
}
