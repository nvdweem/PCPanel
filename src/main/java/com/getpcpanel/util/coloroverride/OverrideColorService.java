package com.getpcpanel.util.coloroverride;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.getpcpanel.profile.SingleKnobLightingConfig;
import com.getpcpanel.profile.SingleLogoLightingConfig;
import com.getpcpanel.profile.SingleSliderLabelLightingConfig;
import com.getpcpanel.profile.SingleSliderLightingConfig;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import one.util.streamex.StreamEx;

@Setter
@Service
@RequiredArgsConstructor
public class OverrideColorService {
    @Autowired @Lazy private List<IOverrideColorProvider> overriders;

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
