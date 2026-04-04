package com.getpcpanel.hid;

import java.util.Arrays;

import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;

import com.getpcpanel.device.DeviceType;
import com.getpcpanel.profile.LightingConfig;
import com.getpcpanel.profile.SingleKnobLightingConfig;
import com.getpcpanel.profile.SingleLogoLightingConfig;
import com.getpcpanel.profile.SingleSliderLabelLightingConfig;
import com.getpcpanel.profile.SingleSliderLightingConfig;
import com.getpcpanel.util.coloroverride.OverrideColorService;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ApplicationScoped
public final class OutputInterpreter {
    @Inject
    DeviceScanner deviceScanner;
    @Inject
    OverrideColorService overrideColorService;

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
    }

    public void sendFullLEDData(String deviceSerialNumber, int brightness, String[] colors, boolean[] volumeTrack, boolean priority) {
        var handler = deviceScanner.getConnectedDevice(deviceSerialNumber);
        if (handler == null)
            throw new IllegalArgumentException("invalid device");

        var data = new ByteWriter(brightness, 2 + 4 * colors.length + colors.length).append(2, 0);
        for (var color : colors) {
            var toSend = overrideColorService.getDialOverride(deviceSerialNumber, 0).map(SingleKnobLightingConfig::getColor1).orElse(color);
            data.append(OUTPUT_CODE_RGB_RGB).appendHex(toSend);
        }
        for (var b : volumeTrack) {
            data.append(b ? 1 : 0);
        }
        if (priority) {
            handler.sendMessage(data.get());
        } else {
            handler.sendMessage(new byte[][] { data.get() });
        }
    }

    public void sendLightingConfig(String serialNumber, DeviceType dt, LightingConfig config, boolean priority) {
        if (dt == null) {
            throw new IllegalArgumentException("Empty device type");
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
                handler.sendMessage(new byte[][] { knobData });
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
                handler.sendMessage(knobData, sliderLabelData, sliderData, logoData);
            }
        }
    }

    private void writeAllColor(DeviceCommunicationHandler handler, byte prefix, byte secondPrefix, LightingConfig config) {
        var c1 = config.allColor();
        var data = new ByteWriter(config.getGlobalBrightness()).append(prefix, MODE_LIGHT_ANIMATION, secondPrefix).appendHex(c1).get();
        handler.sendMessage(new byte[][] { data });
    }

    private void writeAllRainbow(DeviceCommunicationHandler handler, byte prefix, LightingConfig config) {
        var data = new ByteWriter(config.getGlobalBrightness()).append(prefix, MODE_LIGHT_ANIMATION, (config.rainbowVertical() == 1) ? ANIMATION_RAINBOW_VERTICAL : ANIMATION_RAINBOW_HORIZONTAL)
                                                               .append(config.rainbowPhaseShift(),
                                                                       -1)
                                                               .appendBrightness(config.rainbowBrightness())
                                                               .append(config.rainbowSpeed(),
                                                                       config.rainbowReverse())
                                                               .get();
        handler.sendMessage(new byte[][] { data });
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
        handler.sendMessage(new byte[][] { data.get() });
    }

    private void writeAllBreath(DeviceCommunicationHandler handler, byte prefix, LightingConfig config) {
        var data = new ByteWriter(config.getGlobalBrightness())
                .append(prefix, MODE_LIGHT_ANIMATION, ANIMATION_BREATH)
                .append(config.breathHue(),
                        -1)
                .appendBrightness(config.breathBrightness())
                .append(config.breathSpeed());
        handler.sendMessage(new byte[][] { data.get() });
    }

    private byte[] buildKnobData(String deviceSerial, byte prefix, int brightness, SingleKnobLightingConfig[] knobConfigs) {
        var knobData = new ByteWriter(brightness).append(prefix, CUSTOM_KNOB);

        for (var i = 0; i < knobConfigs.length; i++) {
            var knobConfig = overrideColorService.getDialOverride(deviceSerial, i).orElse(knobConfigs[i]);

            knobData.mark();
            var ignored = switch (knobConfig.getMode()) {
                case NONE -> knobData;
                case STATIC -> {
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
            var ignored = switch (sliderLabelConfig.getMode()) {
                case NONE -> sliderLabelData;
                case STATIC -> {
                    var c1 = sliderLabelConfig.getColor();
                    yield sliderLabelData.mark()
                                         .append(1)
                                         .appendHex(c1);
                }
            };
            sliderLabelData.skipFromMark(7);
        }
        return sliderLabelData.get();
    }

    private byte[] buildSliderData(String deviceSerial, int brightness, SingleSliderLightingConfig[] sliderConfigs) {
        var sliderData = new ByteWriter(brightness).append(PREFIX_PRO, CUSTOM_SLIDER);

        for (var i = 0; i < sliderConfigs.length; i++) {
            var sliderConfig = overrideColorService.getSliderOverride(deviceSerial, i).orElse(sliderConfigs[i]);
            sliderData.mark();
            var ignored = switch (sliderConfig.getMode()) {
                case NONE -> sliderData;
                case STATIC -> {
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
        var logoConfig = overrideColorService.getLogoOverride(deviceSerial).orElse(config);
        var logoData = new ByteWriter(brightness).append(PREFIX_PRO, CUSTOM_LOGO);
        var ignored = switch (logoConfig.getMode()) {
            case NONE -> logoConfig;
            case STATIC -> {
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
            default -> log.error("unexpected lighting mode in deviceOutputHandler");
        }
    }

    public void sendRainbow(String deviceSerialNumber, byte phase_shift, byte saturation, byte brightness, byte speed, byte reverse, boolean priority) {
        var handler = deviceScanner.getConnectedDevice(deviceSerialNumber);
        if (handler == null)
            throw new IllegalArgumentException("invalid device");
        var data = new byte[] { OUTPUT_CODE_RGB, OUTPUT_CODE_RGB_RAINBOW, phase_shift, saturation, brightness, speed, reverse };
        if (priority) {
            handler.sendMessage(data);
        } else {
            handler.sendMessage(new byte[][] { data });
        }
    }

    public void sendWave(String deviceSerialNumber, byte hue, byte saturation, byte brightness, byte speed, byte reverse, byte bounce, boolean priority) {
        var handler = deviceScanner.getConnectedDevice(deviceSerialNumber);
        if (handler == null)
            throw new IllegalArgumentException("invalid device");
        var data = new byte[] { OUTPUT_CODE_RGB, OUTPUT_CODE_RGB_WAVE, hue, saturation, brightness, speed, reverse, bounce };
        if (priority) {
            handler.sendMessage(data);
        } else {
            handler.sendMessage(new byte[][] { data });
        }
    }

    public void sendBreath(String deviceSerialNumber, byte hue, byte saturation, byte brightness, byte speed, boolean priority) {
        var handler = deviceScanner.getConnectedDevice(deviceSerialNumber);
        if (handler == null)
            throw new IllegalArgumentException("invalid device");
        var data = new byte[] { OUTPUT_CODE_RGB, OUTPUT_CODE_RGB_BREATH, hue, saturation, brightness, speed };
        if (priority) {
            handler.sendMessage(data);
        } else {
            handler.sendMessage(new byte[][] { data });
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
            } catch (Exception ignored) {}
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
            handler.sendMessage(data.get());
        } else {
            handler.sendMessage(new byte[][] { data.get() });
        }
    }

    private boolean isIntByteSize(int... is) {
        return Arrays.stream(is).noneMatch(i -> i < 0 || i > MAX_BYTE);
    }
}
