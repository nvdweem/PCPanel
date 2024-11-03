package com.getpcpanel.util.coloroverride;

import java.util.Optional;

import com.getpcpanel.profile.SingleKnobLightingConfig;
import com.getpcpanel.profile.SingleLogoLightingConfig;
import com.getpcpanel.profile.SingleSliderLabelLightingConfig;
import com.getpcpanel.profile.SingleSliderLightingConfig;

public interface IOverrideColorProviderProvider extends IOverrideColorProvider {
    IOverrideColorProvider getOverrideColorProvider();

    @Override
    default Optional<SingleKnobLightingConfig> getDialOverride(String deviceSerial, int dial) {
        return getOverrideColorProvider().getDialOverride(deviceSerial, dial);
    }

    @Override
    default Optional<SingleSliderLightingConfig> getSliderOverride(String deviceSerial, int slider) {
        return getOverrideColorProvider().getSliderOverride(deviceSerial, slider);
    }

    @Override
    default Optional<SingleSliderLabelLightingConfig> getSliderLabelOverride(String deviceSerial, int slider) {
        return getOverrideColorProvider().getSliderLabelOverride(deviceSerial, slider);
    }

    @Override
    default Optional<SingleLogoLightingConfig> getLogoOverride(String deviceSerial) {
        return getOverrideColorProvider().getLogoOverride(deviceSerial);
    }
}
