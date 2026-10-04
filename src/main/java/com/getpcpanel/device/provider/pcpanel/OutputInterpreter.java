package com.getpcpanel.device.provider.pcpanel;

import com.getpcpanel.integration.device.BrightnessService;
import com.getpcpanel.integration.visualizer.VisualizerService;

import java.util.Arrays;

import javax.annotation.Nullable;

import com.getpcpanel.device.provider.pcpanel.DeviceType;
import com.getpcpanel.profile.BaseLayerService;
import com.getpcpanel.profile.dto.LightingConfig;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig;
import com.getpcpanel.profile.dto.SingleLogoLightingConfig;
import com.getpcpanel.profile.dto.SingleSliderLabelLightingConfig;
import com.getpcpanel.profile.dto.SingleSliderLightingConfig;
import com.getpcpanel.sleepdetection.SleepDetector;
import com.getpcpanel.util.coloroverride.OverrideColorService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ApplicationScoped
public final class OutputInterpreter {
    @Inject
    DeviceScanner deviceScanner;
    @Inject
    OverrideColorService overrideColorService;
    @Inject
    BaseLayerService baseLayer;
    @Inject
    BrightnessService brightnessService;
    @Inject
    VisualizerService visualizer;
    @Inject
    SleepDetector sleep;

    private static final byte[] OUTPUT_CODE_INIT = { 1 };
    private static final byte ANIMATION_RAINBOW_HORIZONTAL = 1;
    private static final byte ANIMATION_RAINBOW_VERTICAL = 2;
    private static final byte ANIMATION_WAVE = 3;
    private static final byte ANIMATION_BREATH = 4;
    private static final byte COLOR_STATIC = 1;
    private static final byte COLOR_GRADIENT = 2;
    private static final byte CUSTOM_SLIDER = 0;
    private static final byte CUSTOM_SLIDER_LABEL = 1;
    private static final byte CUSTOM_KNOB = 2;
    private static final byte CUSTOM_LOGO = 3;
    private static final byte LOGO_RAINBOW = 2;
    private static final byte LOGO_BREATH = 3;
    private static final byte MODE_LIGHT_ANIMATION = 4;
    private static final byte OUTPUT_CODE_RGB_RGB = 1;
    private static final byte OUTPUT_CODE_RGB = 2;
    private static final byte OUTPUT_CODE_RGB_RAINBOW = 3;
    private static final byte OUTPUT_CODE_RGB_WAVE = 4;
    private static final byte OUTPUT_CODE_RGB_BREATH = 5;
    private static final byte PREFIX_MINI = 6;
    private static final byte PREFIX_PRO = 5;
    private static final int MAX_BYTE = 255;

    public void sendInit(String deviceSerialNumber) {
        var handler = deviceScanner.getConnectedDevice(deviceSerialNumber);
        if (handler == null)
            throw new IllegalArgumentException("invalid device");
        handler.sendMessage(OUTPUT_CODE_INIT);
        if (sleep != null) {
            sleep.panelChanged(deviceSerialNumber); // it shows its power-on lighting now
        }
    }

    public void sendFullLEDData(String deviceSerialNumber, int brightness, String[] colors, boolean[] volumeTrack, boolean priority) {
        var resolved = new String[colors.length];
        var lightBrightness = new Integer[colors.length];
        for (var i = 0; i < colors.length; i++) {
            var override = overrideColorService.getDialOverride(deviceSerialNumber, i);
            resolved[i] = override.map(SingleKnobLightingConfig::getColor1).orElse(colors[i]);
            lightBrightness[i] = override.map(SingleKnobLightingConfig::getOverrideBrightness).orElse(null);
        }
        sendRGBMessage(deviceSerialNumber, buildFullLEDData(brightness, resolved, lightBrightness, volumeTrack), priority);
    }

    /**
     * Per-knob lighting on the RGB. Its firmware has no per-knob gradient, so each knob is sent as a single
     * colour: static knobs as their colour, volume-gradient knobs as their end colour with the LED brightness
     * following the knob position, and off/unset knobs as black.
     */
    private void sendRGBCustom(String serialNumber, LightingConfig config, boolean priority) {
        var knobConfigs = config.knobConfigs();
        var resolved = new SingleKnobLightingConfig[DeviceType.PCPANEL_RGB.getAnalogCount()];
        for (var i = 0; i < resolved.length; i++) {
            var configured = knobConfigs != null && i < knobConfigs.length ? knobConfigs[i] : null;
            resolved[i] = overrideColorService.getDialOverride(serialNumber, i).orElse(configured);
        }
        sendRGBMessage(serialNumber, buildRGBCustomData(config.getGlobalBrightness(), resolved), priority);
    }

    static byte[] buildRGBCustomData(int brightness, SingleKnobLightingConfig[] knobConfigs) {
        var colors = new String[knobConfigs.length];
        var lightBrightness = new Integer[knobConfigs.length];
        var volumeTrack = new boolean[knobConfigs.length];
        for (var i = 0; i < knobConfigs.length; i++) {
            var knob = knobConfigs[i];
            lightBrightness[i] = knob == null ? null : knob.getOverrideBrightness();
            var mode = knob == null || knob.getMode() == null ? SingleKnobLightingConfig.SINGLE_KNOB_MODE.NONE : knob.getMode();
            switch (mode) {
                case NONE -> colors[i] = "#000000";
                case STATIC, AUDIO_LEVEL -> colors[i] = knob.getColor1();
                case VOLUME_GRADIENT -> {
                    colors[i] = knob.getColor2();
                    volumeTrack[i] = true;
                }
            }
        }
        return buildFullLEDData(brightness, colors, lightBrightness, volumeTrack);
    }

    /** {@code lightBrightness} holds, per light, its own brightness; a null entry, or a null array, follows {@code brightness}. */
    static byte[] buildFullLEDData(int brightness, String[] colors, @Nullable Integer[] lightBrightness, boolean[] volumeTrack) {
        var data = new ByteWriter(brightness, 2 + 4 * colors.length + colors.length).append(OUTPUT_CODE_RGB, 0);
        for (var i = 0; i < colors.length; i++) {
            data.light(lightBrightness != null && i < lightBrightness.length ? lightBrightness[i] : null)
                .append(OUTPUT_CODE_RGB_RGB).appendHex(colors[i]);
        }
        for (var i = 0; i < colors.length; i++) {
            data.append(volumeTrack != null && i < volumeTrack.length && volumeTrack[i] ? 1 : 0);
        }
        return data.get();
    }

    private void sendRGBMessage(String serialNumber, byte[] data, boolean priority) {
        var handler = deviceScanner.getConnectedDevice(serialNumber);
        if (handler == null)
            throw new IllegalArgumentException("invalid device");
        if (priority) {
            handler.sendLighting(data);
        } else {
            handler.sendLighting(new byte[][] { data });
        }
    }

    /**
     * A device's own lighting (its profile, a light show frame): like {@link #sendLightingConfig}, but while the music
     * visualizer shows on a profile whose lighting is a single colour or animation, sent as the per-control lighting
     * the visualizer paints, and while the panels show dark frames ({@link SleepDetector#showsDarkFrames()}: locked,
     * with the visualizer or notification lights kept going), the device's dark frame instead. Lighting that overrules
     * the device's (lights off while locked, the dark frame) goes through {@link #sendLightingConfig} and is never
     * replaced.
     */
    public void sendDeviceLighting(String serialNumber, DeviceType dt, LightingConfig config, boolean priority) {
        if (sleep != null && sleep.showsDarkFrames()) {
            sleep.showDarkFrame(serialNumber);
            return;
        }
        sendLightingConfig(serialNumber, dt, visualizer.substitute(serialNumber, config), priority);
    }

    /** A temporary frame (a light show, notification lights over whole-panel lighting), sent as it is. */
    public void sendTemporaryLighting(String serialNumber, DeviceType dt, LightingConfig frame) {
        sendLightingConfig(serialNumber, dt, frame, true);
        if (sleep != null) {
            sleep.panelChanged(serialNumber); // so the next dark frame is sent in full
        }
    }

    public void sendLightingConfig(String serialNumber, DeviceType dt, LightingConfig config, boolean priority) {
        if (dt == null) {
            throw new IllegalArgumentException("Empty device type");
        }
        // Fill any per-control "off" slots from the device's base layer (no-op outside CUSTOM mode / no base).
        config = baseLayer.effectiveLighting(serialNumber, config);
        // A brightness dial (in any profile) drives a runtime global brightness that wins over the saved value.
        var runtimeBrightness = brightnessService.runtimeBrightness(serialNumber);
        if (runtimeBrightness.isPresent()) {
            config = config.deepCopy();
            config.setGlobalBrightness(runtimeBrightness.getAsInt());
        }
        switch (dt) {
            case PCPANEL_RGB -> sendLightingConfigRGB(serialNumber, config, priority);
            case PCPANEL_MINI -> sendLightingConfigMini(serialNumber, config);
            case PCPANEL_PRO -> sendLightingConfigPro(serialNumber, config);
        }
    }

    private void sendLightingConfigMini(String serialNumber, LightingConfig config) {
        var handler = deviceScanner.getConnectedDevice(serialNumber);
        var mode = config.lightingMode();
        if (mode == null) {
            log.error("Null lighting mode in sendLightingConfigMini, ignoring");
            return;
        }
        switch (mode) {
            case ALL_COLOR -> writeAllColor(handler, PREFIX_MINI, (byte) 5, config);
            case ALL_RAINBOW -> writeAllRainbow(handler, PREFIX_MINI, config);
            case ALL_WAVE -> writeAllWave(handler, PREFIX_MINI, config);
            case ALL_BREATH -> writeAllBreath(handler, PREFIX_MINI, config);
            case CUSTOM -> {
                var knobData = buildKnobData(serialNumber, PREFIX_MINI, config.getGlobalBrightness(), config.knobConfigs());
                handler.sendLighting(new byte[][] { knobData });
            }
        }
    }

    private void sendLightingConfigPro(String serialNumber, LightingConfig config) {
        var handler = deviceScanner.getConnectedDevice(serialNumber);
        var mode = config.lightingMode();
        if (mode == null) {
            log.error("Null lighting mode in sendLightingConfigPro, ignoring");
            return;
        }
        switch (mode) {
            case ALL_COLOR -> writeAllColor(handler, PREFIX_PRO, (byte) 2, config);
            case ALL_RAINBOW -> writeAllRainbow(handler, PREFIX_PRO, config);
            case ALL_WAVE -> writeAllWave(handler, PREFIX_PRO, config);
            case ALL_BREATH -> writeAllBreath(handler, PREFIX_PRO, config);
            case CUSTOM -> {
                var knobData = buildKnobData(serialNumber, PREFIX_PRO, config.getGlobalBrightness(), config.knobConfigs());
                var sliderLabelData = buildSliderLabelData(serialNumber, config.getGlobalBrightness(), config.sliderLabelConfigs());
                var sliderData = buildSliderData(serialNumber, config.getGlobalBrightness(), config.sliderConfigs());
                var logoData = buildLogoData(serialNumber, config.getGlobalBrightness(), config.logoConfig());
                handler.sendLighting(knobData, sliderLabelData, sliderData, logoData);
            }
        }
    }

    private void writeAllColor(DeviceCommunicationHandler handler, byte prefix, byte secondPrefix, LightingConfig config) {
        var c1 = config.allColor();
        var data = new ByteWriter(config.getGlobalBrightness()).append(prefix, MODE_LIGHT_ANIMATION, secondPrefix).appendHex(c1).get();
        handler.sendLighting(new byte[][] { data });
    }

    private void writeAllRainbow(DeviceCommunicationHandler handler, byte prefix, LightingConfig config) {
        var data = new ByteWriter(config.getGlobalBrightness()).append(prefix, MODE_LIGHT_ANIMATION, (config.rainbowVertical() == 1) ? ANIMATION_RAINBOW_VERTICAL : ANIMATION_RAINBOW_HORIZONTAL)
                                                               .append(config.rainbowPhaseShift(),
                                                                       -1)
                                                               .appendBrightness(config.rainbowBrightness())
                                                               .append(config.rainbowSpeed(),
                                                                       config.rainbowReverse())
                                                               .get();
        handler.sendLighting(new byte[][] { data });
    }

    private void writeAllWave(DeviceCommunicationHandler handler, byte prefix, LightingConfig config) {
        var data = new ByteWriter(config.getGlobalBrightness())
                .append(prefix, MODE_LIGHT_ANIMATION, ANIMATION_WAVE)
                .append(config.waveHue(),
                        -1)
                .appendBrightness(config.waveBrightness())
                .append(config.waveSpeed(),
                        config.waveReverse(),
                        config.waveBounce());
        handler.sendLighting(new byte[][] { data.get() });
    }

    private void writeAllBreath(DeviceCommunicationHandler handler, byte prefix, LightingConfig config) {
        var data = new ByteWriter(config.getGlobalBrightness())
                .append(prefix, MODE_LIGHT_ANIMATION, ANIMATION_BREATH)
                .append(config.breathHue(),
                        -1)
                .appendBrightness(config.breathBrightness())
                .append(config.breathSpeed());
        handler.sendLighting(new byte[][] { data.get() });
    }

    private byte[] buildKnobData(String deviceSerial, byte prefix, int brightness, SingleKnobLightingConfig[] knobConfigs) {
        var resolved = new SingleKnobLightingConfig[knobConfigs.length];
        for (var i = 0; i < knobConfigs.length; i++) {
            resolved[i] = overrideColorService.getDialOverride(deviceSerial, i).orElse(knobConfigs[i]);
        }
        return buildKnobData(prefix, brightness, resolved);
    }

    /** The knob lights, overrides already applied; each at its own brightness when it has one. */
    static byte[] buildKnobData(byte prefix, int brightness, SingleKnobLightingConfig[] knobConfigs) {
        var knobData = new ByteWriter(brightness).append(prefix, CUSTOM_KNOB);

        for (var knobConfig : knobConfigs) {
            knobData.light(knobConfig.getOverrideBrightness()).mark();
            var ignored = switch (knobConfig.getMode()) {
                case NONE -> knobData;
                // An audio-level light without a live level (no meter here) shows its loud colour.
                case STATIC, AUDIO_LEVEL -> {
                    var c1 = knobConfig.getColor1();
                    yield knobData.append(COLOR_STATIC)
                                  .appendHex(c1);
                }
                case VOLUME_GRADIENT -> {
                    var c1 = knobConfig.getColor1();
                    var c2 = knobConfig.getColor2();
                    yield knobData.append(COLOR_GRADIENT)
                                  .appendHex(c1)
                                  .appendHex(c2);
                }
            };
            knobData.skipFromMark(7);
        }
        return knobData.get();
    }

    private byte[] buildSliderLabelData(String deviceSerial, int brightness, SingleSliderLabelLightingConfig[] sliderLabelConfigs) {
        var sliderLabelData = new ByteWriter(brightness).append(PREFIX_PRO, CUSTOM_SLIDER_LABEL);

        for (var i = 0; i < sliderLabelConfigs.length; i++) {
            var sliderLabelConfig = overrideColorService.getSliderLabelOverride(deviceSerial, i).orElse(sliderLabelConfigs[i]);
            sliderLabelData.mark();
            var ignored = switch (sliderLabelConfig.getMode()) {
                case NONE -> sliderLabelData;
                case STATIC -> {
                    var c1 = sliderLabelConfig.getColor();
                    yield sliderLabelData.append(1)
                                         .appendHex(c1);
                }
            };
            sliderLabelData.skipFromMark(7);
        }
        return sliderLabelData.get();
    }

    private byte[] buildSliderData(String deviceSerial, int brightness, SingleSliderLightingConfig[] sliderConfigs) {
        var resolved = new SingleSliderLightingConfig[sliderConfigs.length];
        for (var i = 0; i < sliderConfigs.length; i++) {
            resolved[i] = overrideColorService.getSliderOverride(deviceSerial, i).orElse(sliderConfigs[i]);
        }
        return buildSliderData(brightness, resolved);
    }

    /** The slider lights, overrides already applied; each at its own brightness when it has one. */
    static byte[] buildSliderData(int brightness, SingleSliderLightingConfig[] sliderConfigs) {
        var sliderData = new ByteWriter(brightness).append(PREFIX_PRO, CUSTOM_SLIDER);

        for (var sliderConfig : sliderConfigs) {
            sliderData.light(sliderConfig.getOverrideBrightness()).mark();
            var ignored = switch (sliderConfig.getMode()) {
                case NONE -> sliderData;
                case STATIC, AUDIO_LEVEL -> {
                    var c1 = sliderConfig.getColor1();
                    yield sliderData.append(1)
                                    .appendHex(c1)
                                    .appendHex(c1);
                }
                case STATIC_GRADIENT -> sliderData.append(1)
                                                  .appendHex(sliderConfig.getColor1())
                                                  .appendHex(sliderConfig.getColor2());
                case VOLUME_GRADIENT -> sliderData.append(3)
                                                  .appendHex(sliderConfig.getColor1())
                                                  .appendHex(sliderConfig.getColor2());
            };
            sliderData.skipFromMark(7);
        }
        return sliderData.get();
    }

    private byte[] buildLogoData(String deviceSerial, int brightness, SingleLogoLightingConfig config) {
        return buildLogoData(brightness, overrideColorService.getLogoOverride(deviceSerial).orElse(config));
    }

    /** The logo light, an override already applied; at its own brightness when it has one. */
    static byte[] buildLogoData(int brightness, SingleLogoLightingConfig logoConfig) {
        var logoData = new ByteWriter(brightness).light(logoConfig.getOverrideBrightness()).append(PREFIX_PRO, CUSTOM_LOGO);
        var ignored = switch (logoConfig.getMode()) {
            case NONE -> logoConfig;
            case STATIC, AUDIO_LEVEL -> {
                var c1 = logoConfig.getColor();
                yield logoData.append(COLOR_STATIC).appendHex(c1);
            }
            case RAINBOW -> logoData.append(LOGO_RAINBOW)
                                    .append(-1)
                                    .appendBrightness(logoConfig.getBrightness())
                                    .append(logoConfig.getSpeed());
            case BREATH -> logoData.append(LOGO_BREATH)
                                   .append(logoConfig.getHue(),
                                           -1)
                                   .appendBrightness(logoConfig.getBrightness())
                                   .append(logoConfig.getSpeed());
        };
        return logoData.get();
    }

    private void sendLightingConfigRGB(String serialNumber, LightingConfig config, boolean priority) {
        var mode = config.lightingMode();
        if (mode == null) {
            log.error("unexpected lighting mode in deviceOutputHandler");
            return;
        }

        switch (mode) {
            case ALL_COLOR -> sendRGBAll(serialNumber, config.getGlobalBrightness(), config.allColor(), config.volumeBrightnessTrackingEnabled(), priority);
            case SINGLE_COLOR -> sendFullLEDData(serialNumber, config.getGlobalBrightness(), config.individualColors(), config.volumeBrightnessTrackingEnabled(), priority);
            case ALL_RAINBOW -> sendRainbow(serialNumber, config.rainbowPhaseShift(), (byte) -1, config.rainbowBrightness(), config.rainbowSpeed(), config.rainbowReverse(), priority);
            case ALL_WAVE -> sendWave(serialNumber, config.waveHue(), (byte) -1, config.waveBrightness(), config.waveSpeed(), config.waveReverse(), config.waveBounce(), priority);
            case ALL_BREATH -> sendBreath(serialNumber, config.breathHue(), (byte) -1, config.breathBrightness(), config.breathSpeed(), priority);
            case CUSTOM -> sendRGBCustom(serialNumber, config, priority);
        }
    }

    public void sendRainbow(String deviceSerialNumber, byte phase_shift, byte saturation, byte brightness, byte speed, byte reverse, boolean priority) {
        var handler = deviceScanner.getConnectedDevice(deviceSerialNumber);
        if (handler == null)
            throw new IllegalArgumentException("invalid device");
        var data = new byte[] { OUTPUT_CODE_RGB, OUTPUT_CODE_RGB_RAINBOW, phase_shift, saturation, brightness, speed, reverse };
        if (priority) {
            handler.sendLighting(data);
        } else {
            handler.sendLighting(new byte[][] { data });
        }
    }

    public void sendWave(String deviceSerialNumber, byte hue, byte saturation, byte brightness, byte speed, byte reverse, byte bounce, boolean priority) {
        var handler = deviceScanner.getConnectedDevice(deviceSerialNumber);
        if (handler == null)
            throw new IllegalArgumentException("invalid device");
        var data = new byte[] { OUTPUT_CODE_RGB, OUTPUT_CODE_RGB_WAVE, hue, saturation, brightness, speed, reverse, bounce };
        if (priority) {
            handler.sendLighting(data);
        } else {
            handler.sendLighting(new byte[][] { data });
        }
    }

    public void sendBreath(String deviceSerialNumber, byte hue, byte saturation, byte brightness, byte speed, boolean priority) {
        var handler = deviceScanner.getConnectedDevice(deviceSerialNumber);
        if (handler == null)
            throw new IllegalArgumentException("invalid device");
        var data = new byte[] { OUTPUT_CODE_RGB, OUTPUT_CODE_RGB_BREATH, hue, saturation, brightness, speed };
        if (priority) {
            handler.sendLighting(data);
        } else {
            handler.sendLighting(new byte[][] { data });
        }
    }

    @SuppressWarnings("NumericCastThatLosesPrecision")
    public void sendRGBAll(String deviceSerialNumber, int brightness, String hexColor, boolean[] bs, boolean priority) {
        int r = 0, g = 0, b = 0;
        if (hexColor != null) {
            try {
                String hex = hexColor.startsWith("#") ? hexColor.substring(1) : hexColor;
                r = Integer.parseInt(hex.substring(0, 2), 16);
                g = Integer.parseInt(hex.substring(2, 4), 16);
                b = Integer.parseInt(hex.substring(4, 6), 16);
            } catch (Exception ignored) {
            }
        }
        sendRGBAll(deviceSerialNumber, brightness, r, g, b, bs, priority);
    }

    public void sendRGBAll(String deviceSerialNumber, int brightness, int red, int green, int blue, boolean[] volumeTrack, boolean priority) {
        var handler = deviceScanner.getConnectedDevice(deviceSerialNumber);
        if (handler == null)
            throw new IllegalArgumentException("invalid device");
        if (!isIntByteSize(red, green, blue))
            throw new IllegalArgumentException("ints must be byte size");
        var data = new ByteWriter(brightness, 6 + volumeTrack.length)
                .append(OUTPUT_CODE_RGB, OUTPUT_CODE_RGB_RGB, 0)
                .appendRGB(red, green, blue);
        for (var b : volumeTrack)
            data.append(b ? 1 : 0);
        if (priority) {
            handler.sendLighting(data.get());
        } else {
            handler.sendLighting(new byte[][] { data.get() });
        }
    }

    private boolean isIntByteSize(int... is) {
        return Arrays.stream(is).noneMatch(i -> i < 0 || i > MAX_BYTE);
    }
}
