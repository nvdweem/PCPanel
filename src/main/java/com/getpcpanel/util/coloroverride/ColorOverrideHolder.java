package com.getpcpanel.util.coloroverride;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;

import javax.annotation.Nullable;

import com.getpcpanel.profile.dto.SingleKnobLightingConfig;
import com.getpcpanel.profile.dto.SingleLogoLightingConfig;
import com.getpcpanel.profile.dto.SingleSliderLabelLightingConfig;
import com.getpcpanel.profile.dto.SingleSliderLightingConfig;

public class ColorOverrideHolder implements IOverrideColorProvider {
    // All access is synchronized on this holder: the per-device OverrideColors hold plain HashMaps that
    // the mute-colour recompute mutates (setX) while the HID output / UI colour threads read them (getX).
    // The new recompute-on-every-audio-event path makes that read/write race frequent, so serialize it.
    private final Map<String, OverrideColors> overrides = Collections.synchronizedMap(new HashMap<>());

    public synchronized void setDialOverride(String deviceSerial, int dial, @Nullable SingleKnobLightingConfig config) {
        overrides.computeIfAbsent(deviceSerial, s -> new OverrideColors())
                .dials.put(dial, config);
    }

    public synchronized void setSliderOverride(String deviceSerial, int slider, @Nullable SingleSliderLightingConfig config) {
        overrides.computeIfAbsent(deviceSerial, s -> new OverrideColors())
                .sliders.put(slider, config);
    }

    public synchronized void setSliderLabelOverride(String deviceSerial, int slider, @Nullable SingleSliderLabelLightingConfig config) {
        overrides.computeIfAbsent(deviceSerial, s -> new OverrideColors())
                .sliderLabels.put(slider, config);
    }

    public synchronized void setLogoOverride(String deviceSerial, @Nullable SingleLogoLightingConfig config) {
        overrides.computeIfAbsent(deviceSerial, s -> new OverrideColors())
                .logo.set(config);
    }

    public synchronized void clearAllOverrides() {
        overrides.clear();
    }

    @Override
    public synchronized Optional<SingleKnobLightingConfig> getDialOverride(String deviceSerial, int dial) {
        return Optional.ofNullable(overrides.getOrDefault(deviceSerial, OverrideColors.EMPTY).dials.get(dial));
    }

    @Override
    public synchronized Optional<SingleSliderLightingConfig> getSliderOverride(String deviceSerial, int slider) {
        return Optional.ofNullable(overrides.getOrDefault(deviceSerial, OverrideColors.EMPTY).sliders.get(slider));
    }

    @Override
    public synchronized Optional<SingleSliderLabelLightingConfig> getSliderLabelOverride(String deviceSerial, int slider) {
        return Optional.ofNullable(overrides.getOrDefault(deviceSerial, OverrideColors.EMPTY).sliderLabels.get(slider));
    }

    @Override
    public synchronized Optional<SingleLogoLightingConfig> getLogoOverride(String deviceSerial) {
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
