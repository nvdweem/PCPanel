package com.getpcpanel.util.coloroverride;

import java.util.Optional;

import com.getpcpanel.profile.SingleKnobLightingConfig;
import com.getpcpanel.profile.SingleLogoLightingConfig;
import com.getpcpanel.profile.SingleSliderLabelLightingConfig;
import com.getpcpanel.profile.SingleSliderLightingConfig;

public interface IOverrideColorProvider {
    Optional<SingleKnobLightingConfig> getDialOverride(String deviceSerial, int dial);

    Optional<SingleSliderLightingConfig> getSliderOverride(String deviceSerial, int slider);

    Optional<SingleSliderLabelLightingConfig> getSliderLabelOverride(String deviceSerial, int slider);

    Optional<SingleLogoLightingConfig> getLogoOverride(String deviceSerial);
}
