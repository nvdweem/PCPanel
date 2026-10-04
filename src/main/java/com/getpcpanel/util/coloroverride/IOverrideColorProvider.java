package com.getpcpanel.util.coloroverride;

import java.util.Optional;

import com.getpcpanel.profile.dto.SingleKnobLightingConfig;
import com.getpcpanel.profile.dto.SingleLogoLightingConfig;
import com.getpcpanel.profile.dto.SingleSliderLabelLightingConfig;
import com.getpcpanel.profile.dto.SingleSliderLightingConfig;

public interface IOverrideColorProvider {
    Optional<SingleKnobLightingConfig> getDialOverride(String deviceSerial, int dial);

    Optional<SingleSliderLightingConfig> getSliderOverride(String deviceSerial, int slider);

    Optional<SingleSliderLabelLightingConfig> getSliderLabelOverride(String deviceSerial, int slider);

    Optional<SingleLogoLightingConfig> getLogoOverride(String deviceSerial);

    /**
     * Whether these overrides show while the panels are dark for a lock or screens off (see
     * {@link com.getpcpanel.sleepdetection.SleepDetector#showsDarkFrames()}); the others are left out meanwhile.
     */
    default boolean showsWhileDark() {
        return false;
    }
}
