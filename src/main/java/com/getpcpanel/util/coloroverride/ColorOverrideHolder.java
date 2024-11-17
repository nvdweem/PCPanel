package com.getpcpanel.util.coloroverride;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.getpcpanel.profile.SingleKnobLightingConfig;
import com.getpcpanel.profile.SingleLogoLightingConfig;
import com.getpcpanel.profile.SingleSliderLabelLightingConfig;
import com.getpcpanel.profile.SingleSliderLightingConfig;

public class ColorOverrideHolder implements IOverrideColorProvider {
    private final Map<String, OverrideColors> overrides = Collections.synchronizedMap(new HashMap<>());

    public void setDialOverride(String deviceSerial, int dial, @Nullable SingleKnobLightingConfig config) {
        overrides.computeIfAbsent(deviceSerial, s -> new OverrideColors())
                .dials.put(dial, config);
    }

    public void setSliderOverride(String deviceSerial, int slider, @Nullable SingleSliderLightingConfig config) {
        overrides.computeIfAbsent(deviceSerial, s -> new OverrideColors())
                .sliders.put(slider, config);
    }

    public void setSliderLabelOverride(String deviceSerial, int slider, @Nullable SingleSliderLabelLightingConfig config) {
        overrides.computeIfAbsent(deviceSerial, s -> new OverrideColors())
                .sliderLabels.put(slider, config);
    }

    public void setLogoOverride(String deviceSerial, @Nullable SingleLogoLightingConfig config) {
        overrides.computeIfAbsent(deviceSerial, s -> new OverrideColors())
                .logo.set(config);
    }

    public void clearAllOverrides() {
        overrides.clear();
    }

    @Override
    public Optional<SingleKnobLightingConfig> getDialOverride(String deviceSerial, int dial) {
        return Optional.ofNullable(overrides.getOrDefault(deviceSerial, OverrideColors.EMPTY).dials.get(dial));
    }

    @Override
    public Optional<SingleSliderLightingConfig> getSliderOverride(String deviceSerial, int slider) {
        return Optional.ofNullable(overrides.getOrDefault(deviceSerial, OverrideColors.EMPTY).sliders.get(slider));
    }

    @Override
    public Optional<SingleSliderLabelLightingConfig> getSliderLabelOverride(String deviceSerial, int slider) {
        return Optional.ofNullable(overrides.getOrDefault(deviceSerial, OverrideColors.EMPTY).sliderLabels.get(slider));
    }

    @Override
    public Optional<SingleLogoLightingConfig> getLogoOverride(String deviceSerial) {
        return Optional.ofNullable(overrides.getOrDefault(deviceSerial, OverrideColors.EMPTY).logo.get());
    }

    // Very mutable record!
    record OverrideColors(
            @Nonnull Map<Integer, SingleKnobLightingConfig> dials,
            @Nonnull Map<Integer, SingleSliderLightingConfig> sliders,
            @Nonnull Map<Integer, SingleSliderLabelLightingConfig> sliderLabels,
            @Nonnull AtomicReference<SingleLogoLightingConfig> logo
    ) {
        public static final OverrideColors EMPTY = new OverrideColors();

        public OverrideColors() {
            this(Map.of(), Map.of(), Map.of(), new AtomicReference<>(null));
        }

        OverrideColors {
            dials = new HashMap<>(dials);
            sliders = new HashMap<>(sliders);
            sliderLabels = new HashMap<>(sliderLabels);
        }
    }
}
