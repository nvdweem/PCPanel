package com.getpcpanel.ui;

import java.util.List;
import java.util.function.Supplier;

import com.getpcpanel.profile.LightingConfig;

public final class LightingPresets {
    private LightingPresets() {
    }

    public record Preset(String name, Supplier<LightingConfig> factory) {
        public LightingConfig create() {
            return factory.get();
        }
    }

    public static List<Preset> getPresets() {
        return List.of(
                rainbow("Rainbow Calm", 90, 200, 40, false),
                rainbow("Rainbow Fast", 120, 255, 220, false),
                rainbow("Rainbow Reverse", 120, 255, 160, true),
                rainbow("Rainbow Soft", 60, 140, 80, false),
                wave("Wave Ocean", 160, 210, 120, false, false),
                wave("Wave Fire", 12, 255, 160, false, false),
                wave("Wave Ice", 195, 230, 110, true, false),
                wave("Wave Bounce", 30, 240, 140, false, true),
                wave("Wave Reverse", 110, 230, 150, true, false),
                wave("Wave Neon", 210, 255, 200, false, true),
                breath("Breath Calm", 160, 200, 40),
                breath("Breath Slow", 210, 190, 20),
                breath("Breath Fast", 0, 255, 200),
                breath("Breath Deep", 130, 220, 70),
                breath("Breath Pulse", 30, 255, 140),
                breath("Breath Chill", 185, 200, 90)
        );
    }

    private static Preset rainbow(String name, int phaseShift, int brightness, int speed, boolean reverse) {
        return new Preset(name,
                () -> LightingConfig.createRainbowAnimation(b(phaseShift), b(brightness), b(speed), reverse));
    }

    private static Preset wave(String name, int hue, int brightness, int speed, boolean reverse, boolean bounce) {
        return new Preset(name,
                () -> LightingConfig.createWaveAnimation(b(hue), b(brightness), b(speed), reverse, bounce));
    }

    private static Preset breath(String name, int hue, int brightness, int speed) {
        return new Preset(name,
                () -> LightingConfig.createBreathAnimation(b(hue), b(brightness), b(speed)));
    }

    private static byte b(int value) {
        return (byte) value;
    }
}
