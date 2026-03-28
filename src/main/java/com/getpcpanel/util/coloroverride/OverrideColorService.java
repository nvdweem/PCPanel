package com.getpcpanel.util.coloroverride;

import java.util.Optional;

import com.getpcpanel.profile.SingleKnobLightingConfig;
import com.getpcpanel.profile.SingleLogoLightingConfig;
import com.getpcpanel.profile.SingleSliderLabelLightingConfig;
import com.getpcpanel.profile.SingleSliderLightingConfig;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import one.util.streamex.StreamEx;

@Setter
@ApplicationScoped
@RequiredArgsConstructor
public class OverrideColorService {
    @Inject @Any Instance<IOverrideColorProvider> overriders;

    public Optional<SingleKnobLightingConfig> getDialOverride(String deviceSerial, int dial) {
        return StreamEx.of(overriders.stream()).mapPartial(p -> p.getDialOverride(deviceSerial, dial)).findFirst();
    }

    public Optional<SingleSliderLightingConfig> getSliderOverride(String deviceSerial, int slider) {
        return StreamEx.of(overriders.stream()).mapPartial(p -> p.getSliderOverride(deviceSerial, slider)).findFirst();
    }

    public Optional<SingleSliderLabelLightingConfig> getSliderLabelOverride(String deviceSerial, int slider) {
        return StreamEx.of(overriders.stream()).mapPartial(p -> p.getSliderLabelOverride(deviceSerial, slider)).findFirst();
    }

    public Optional<SingleLogoLightingConfig> getLogoOverride(String deviceSerial) {
        return StreamEx.of(overriders.stream()).mapPartial(p -> p.getLogoOverride(deviceSerial)).findFirst();
    }
}
