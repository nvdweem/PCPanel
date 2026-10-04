package com.getpcpanel.util.coloroverride;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.getpcpanel.device.lightshow.LightShow;

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
    /** Null only in tests that build the service by hand. */
    @Inject @Nullable LightShow lightShow;

    /** A light show paints every light itself; overrides would draw over its frames. */
    private boolean held(String deviceSerial) {
        return lightShow != null && lightShow.isRunning(deviceSerial);
    }

    public Optional<SingleKnobLightingConfig> getDialOverride(String deviceSerial, int dial) {
        if (held(deviceSerial)) {
            return Optional.empty();
        }
        return StreamEx.of(overriders).mapPartial(p -> p.getDialOverride(deviceSerial, dial)).findFirst();
    }

    public Optional<SingleSliderLightingConfig> getSliderOverride(String deviceSerial, int slider) {
        if (held(deviceSerial)) {
            return Optional.empty();
        }
        return StreamEx.of(overriders).mapPartial(p -> p.getSliderOverride(deviceSerial, slider)).findFirst();
    }

    public Optional<SingleSliderLabelLightingConfig> getSliderLabelOverride(String deviceSerial, int slider) {
        if (held(deviceSerial)) {
            return Optional.empty();
        }
        return StreamEx.of(overriders).mapPartial(p -> p.getSliderLabelOverride(deviceSerial, slider)).findFirst();
    }

    public Optional<SingleLogoLightingConfig> getLogoOverride(String deviceSerial) {
        if (held(deviceSerial)) {
            return Optional.empty();
        }
        return StreamEx.of(overriders).mapPartial(p -> p.getLogoOverride(deviceSerial)).findFirst();
    }
}
