package com.getpcpanel.util.coloroverride;

import java.util.List;
import java.util.Optional;

import com.getpcpanel.profile.dto.SingleKnobLightingConfig;
import com.getpcpanel.profile.dto.SingleLogoLightingConfig;
import com.getpcpanel.profile.dto.SingleSliderLabelLightingConfig;
import com.getpcpanel.profile.dto.SingleSliderLightingConfig;

import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Setter;
import one.util.streamex.StreamEx;

@Setter
@ApplicationScoped
public class OverrideColorService {
    @Inject @All private List<IOverrideColorProvider> overriders;

    public Optional<SingleKnobLightingConfig> getDialOverride(String deviceSerial, int dial) {
        return StreamEx.of(overriders).mapPartial(p -> p.getDialOverride(deviceSerial, dial)).findFirst();
    }

    public Optional<SingleSliderLightingConfig> getSliderOverride(String deviceSerial, int slider) {
        return StreamEx.of(overriders).mapPartial(p -> p.getSliderOverride(deviceSerial, slider)).findFirst();
    }

    public Optional<SingleSliderLabelLightingConfig> getSliderLabelOverride(String deviceSerial, int slider) {
        return StreamEx.of(overriders).mapPartial(p -> p.getSliderLabelOverride(deviceSerial, slider)).findFirst();
    }

    public Optional<SingleLogoLightingConfig> getLogoOverride(String deviceSerial) {
        return StreamEx.of(overriders).mapPartial(p -> p.getLogoOverride(deviceSerial)).findFirst();
    }
}
