package hid;

import javafx.scene.paint.Color;
import lombok.extern.log4j.Log4j2;
import main.DeviceType;
import save.*;
import save.SingleKnobLightingConfig.SINGLE_KNOB_MODE;
import save.SingleLogoLightingConfig.SINGLE_LOGO_MODE;
import save.SingleSliderLabelLightingConfig.SINGLE_SLIDER_LABEL_MODE;
import save.SingleSliderLightingConfig.SINGLE_SLIDER_MODE;

import java.util.Arrays;

@Log4j2
public final class OutputInterpreter {
    private static final byte[] OUTPUT_CODE_INIT = {1};
    private static final byte ANIMATION_RAINBOW_HORIZONTAL = 1;
    private static final byte ANIMATION_RAINBOW_VERTICAL = 2;
    private static final byte ANIMATION_WAVE = 3;
    private static final byte ANIMATION_BREATH = 4;
    private static final byte PREFIX_PRO = 5;
    private static final byte PREFIX_MINI = 6;
    private static final byte MODE_LIGHT_ANIMATION = 4;
    private static final byte CUSTOM_SLIDER_LABEL = 1;
    private static final byte CUSTOM_KNOB = 2;
    private static final byte CUSTOM_SLIDER = 0;
    private static final byte CUSTOM_LOGO = 3;
    private static final byte COLOR_STATIC = 1;
    private static final byte LOGO_RAINBOW = 2;
    private static final byte LOGO_BREATH = 3;
    private static final byte COLOR_GRADIENT = 2;
    private static final byte OUTPUT_CODE_RGB = 2;
    private static final byte OUTPUT_CODE_RGB_RGB = 1;
    private static final byte OUTPUT_CODE_RGB_RAINBOW = 3;
    private static final byte OUTPUT_CODE_RGB_WAVE = 4;
    private static final byte OUTPUT_CODE_RGB_BREATH = 5;
    private static final int MAX_BYTE = 255;

    private OutputInterpreter() {
    }

    public static void sendInit(String deviceSerialNumber) {
        var handler = DeviceScanner.CONNECTED_DEVICE_MAP.get(deviceSerialNumber);
        if (handler == null)
            throw new IllegalArgumentException("invalid device");
        handler.addToPriorityQueue(OUTPUT_CODE_INIT);
    }

    public static void sendFullLEDData(String deviceSerialNumber, String[] colors, boolean[] volumeTrack, boolean priority) {
        var handler = DeviceScanner.CONNECTED_DEVICE_MAP.get(deviceSerialNumber);
        if (handler == null)
            throw new IllegalArgumentException("invalid device");

        var data = new ByteWriter(2 + 4 * colors.length + colors.length).append(2, 0);
        for (var color : colors) {
            data.append(OUTPUT_CODE_RGB_RGB).append(Color.valueOf(color));
        }
        for (var b : volumeTrack) {
            data.append(b ? 1 : 0);
        }
        if (priority) {
            handler.addToPriorityQueue(data.get());
        } else {
            handler.publishRGBUpdate(new byte[][]{data.get()});
        }
    }

    public static void sendLightingConfig(String serialNumber, DeviceType dt, LightingConfig config, boolean priority) {
        switch (dt) {
            case PCPANEL_RGB -> sendLightingConfigRGB(serialNumber, config, priority);
            case PCPANEL_MINI -> sendLightingConfigMini(serialNumber, config, priority);
            case PCPANEL_PRO -> sendLightingConfigPro(serialNumber, config, priority);
            case null -> throw new IllegalArgumentException("Empty device type");
        }
    }

    private static void sendLightingConfigMini(String serialNumber, LightingConfig config, boolean priority) {
        var handler = DeviceScanner.CONNECTED_DEVICE_MAP.get(serialNumber);
        var mode = config.getLightingMode();
        switch (mode) {
            case ALL_COLOR -> writeAllColor(handler, PREFIX_MINI, (byte) 5, config, priority);
            case ALL_RAINBOW -> writeAllRainbow(handler, PREFIX_MINI, config, priority);
            case ALL_WAVE -> writeAllWave(handler, PREFIX_MINI, config, priority);
            case ALL_BREATH -> writeAllBreath(handler, PREFIX_MINI, config, priority);
            case CUSTOM -> {
                var knobData = buildKnobData(PREFIX_MINI, config.getKnobConfigs());
                handler.sendMessage(priority, new byte[][]{knobData});
            }
        }
    }

    private static void sendLightingConfigPro(String serialNumber, LightingConfig config, boolean priority) {
        var handler = DeviceScanner.CONNECTED_DEVICE_MAP.get(serialNumber);
        var mode = config.getLightingMode();
        switch (mode) {
            case ALL_COLOR -> writeAllColor(handler, PREFIX_PRO, (byte) 2, config, priority);
            case ALL_RAINBOW -> writeAllRainbow(handler, PREFIX_PRO, config, priority);
            case ALL_WAVE -> writeAllWave(handler, PREFIX_PRO, config, priority);
            case ALL_BREATH -> writeAllBreath(handler, PREFIX_PRO, config, priority);
            case CUSTOM -> {
                var knobData = buildKnobData(PREFIX_PRO, config.getKnobConfigs());
                var sliderLabelData = buildSliderLabelData(config.getSliderLabelConfigs());
                var sliderData = buildSliderData(config.getSliderConfigs());
                var logoData = buildLogoData(config.getLogoConfig());
                handler.sendMessage(priority, knobData, sliderLabelData, sliderData, logoData);
            }
        }
    }

    private static void writeAllColor(DeviceCommunicationHandler handler, byte prefix, byte secondPrefix, LightingConfig config, boolean priority) {
        var c1 = Color.valueOf(config.getAllColor());
        var data = new ByteWriter().append(prefix, MODE_LIGHT_ANIMATION, secondPrefix).append(c1).get();
        handler.sendMessage(priority, new byte[][]{data});
    }

    private static void writeAllRainbow(DeviceCommunicationHandler handler, byte prefix, LightingConfig config, boolean priority) {
        var data = new ByteWriter().append(prefix, MODE_LIGHT_ANIMATION, (config.getRainbowVertical() == 1) ? ANIMATION_RAINBOW_VERTICAL : ANIMATION_RAINBOW_HORIZONTAL)
                .append(config.getRainbowPhaseShift(),
                        -1,
                        config.getRainbowBrightness(),
                        config.getRainbowSpeed(),
                        config.getRainbowReverse())
                .get();
        handler.sendMessage(priority, new byte[][]{data});
    }

    private static void writeAllWave(DeviceCommunicationHandler handler, byte prefix, LightingConfig config, boolean priority) {
        var data = new ByteWriter()
                .append(prefix, MODE_LIGHT_ANIMATION, ANIMATION_WAVE)
                .append(config.getWaveHue(),
                        -1,
                        config.getWaveBrightness(),
                        config.getWaveSpeed(),
                        config.getWaveReverse(),
                        config.getWaveBounce());
        handler.sendMessage(priority, new byte[][]{data.get()});
    }

    private static void writeAllBreath(DeviceCommunicationHandler handler, byte prefix, LightingConfig config, boolean priority) {
        var data = new ByteWriter()
                .append(prefix, MODE_LIGHT_ANIMATION, ANIMATION_BREATH)
                .append(config.getBreathHue(),
                        -1,
                        config.getBreathBrightness(),
                        config.getBreathSpeed());
        handler.sendMessage(priority, new byte[][]{data.get()});
    }

    private static byte[] buildKnobData(byte prefix, SingleKnobLightingConfig[] knobConfigs) {
        var knobData = new ByteWriter().append(prefix, CUSTOM_KNOB);
        for (var knobConfig : knobConfigs) {
            knobData.mark();
            if (knobConfig.getMode() == SINGLE_KNOB_MODE.STATIC) {
                var c1 = Color.valueOf(knobConfig.getColor1());
                knobData.append(COLOR_STATIC)
                        .append(c1);
            } else if (knobConfig.getMode() == SINGLE_KNOB_MODE.VOLUME_GRADIENT) {
                var c1 = Color.valueOf(knobConfig.getColor1());
                var c2 = Color.valueOf(knobConfig.getColor2());
                knobData.append(COLOR_GRADIENT)
                        .append(c1)
                        .append(c2);
            }
            knobData.skipFromMark(7);
        }
        return knobData.get();
    }

    private static byte[] buildSliderLabelData(SingleSliderLabelLightingConfig[] sliderLabelConfigs) {
        var sliderLabelData = new ByteWriter().append(PREFIX_PRO, CUSTOM_SLIDER_LABEL);
        for (var sliderLabelConfig : sliderLabelConfigs) {
            if (sliderLabelConfig.getMode() == SINGLE_SLIDER_LABEL_MODE.STATIC) {
                var c1 = Color.valueOf(sliderLabelConfig.getColor());
                sliderLabelData.mark()
                        .append(1)
                        .append(c1);
            }
            sliderLabelData.skipFromMark(7);
        }
        return sliderLabelData.get();
    }

    private static byte[] buildSliderData(SingleSliderLightingConfig[] sliderConfigs) {
        var sliderData = new ByteWriter().append(PREFIX_PRO, CUSTOM_SLIDER);
        for (var sliderConfig : sliderConfigs) {
            sliderData.mark();
            if (sliderConfig.getMode() == SINGLE_SLIDER_MODE.STATIC) {
                var c1 = Color.valueOf(sliderConfig.getColor1());
                sliderData.append(1)
                        .append(c1)
                        .append(c1);
            } else if (sliderConfig.getMode() == SINGLE_SLIDER_MODE.STATIC_GRADIENT) {
                sliderData.append(1)
                        .append(Color.valueOf(sliderConfig.getColor1()))
                        .append(Color.valueOf(sliderConfig.getColor2()));

            } else if (sliderConfig.getMode() == SINGLE_SLIDER_MODE.VOLUME_GRADIENT) {
                sliderData.append(3)
                        .append(Color.valueOf(sliderConfig.getColor1()))
                        .append(Color.valueOf(sliderConfig.getColor2()));
            }
            sliderData.skipFromMark(7);
        }
        return sliderData.get();
    }

    private static byte[] buildLogoData(SingleLogoLightingConfig logoConfig) {
        var logoData = new ByteWriter().append(PREFIX_PRO, CUSTOM_LOGO);
        if (logoConfig.getMode() == SINGLE_LOGO_MODE.STATIC) {
            var c1 = Color.valueOf(logoConfig.getColor());
            logoData.append(COLOR_STATIC).append(c1);
        } else if (logoConfig.getMode() == SINGLE_LOGO_MODE.RAINBOW) {
            logoData.append(LOGO_RAINBOW)
                    .append(-1,
                            logoConfig.getBrightness(),
                            logoConfig.getSpeed());
        } else if (logoConfig.getMode() == SINGLE_LOGO_MODE.BREATH) {
            logoData.append(LOGO_BREATH)
                    .append(logoConfig.getHue(),
                            -1,
                            logoConfig.getBrightness(),
                            logoConfig.getSpeed());
        }
        return logoData.get();
    }

    private static void sendLightingConfigRGB(String serialNumber, LightingConfig config, boolean priority) {
        var mode = config.getLightingMode();
        switch (mode) {
            case ALL_COLOR ->
                    sendRGBAll(serialNumber, Color.valueOf(config.getAllColor()), config.getVolumeBrightnessTrackingEnabled(), priority);
            case SINGLE_COLOR ->
                    sendFullLEDData(serialNumber, config.getIndividualColors(), config.getVolumeBrightnessTrackingEnabled(), priority);
            case ALL_RAINBOW ->
                    sendRainbow(serialNumber, config.getRainbowPhaseShift(), (byte) -1, config.getRainbowBrightness(), config.getRainbowSpeed(), config.getRainbowReverse(), priority);
            case ALL_WAVE ->
                    sendWave(serialNumber, config.getWaveHue(), (byte) -1, config.getWaveBrightness(), config.getWaveSpeed(), config.getWaveReverse(), config.getWaveBounce(), priority);
            case ALL_BREATH ->
                    sendBreath(serialNumber, config.getBreathHue(), (byte) -1, config.getBreathBrightness(), config.getBreathSpeed(), priority);
            case null, default -> log.error("unexpected lighting mode in deviceOutputHandler");
        }
    }

    public static void sendRainbow(String deviceSerialNumber, byte phase_shift, byte saturation, byte brightness, byte speed, byte reverse, boolean priority) {
        var handler = DeviceScanner.CONNECTED_DEVICE_MAP.get(deviceSerialNumber);
        if (handler == null)
            throw new IllegalArgumentException("invalid device");
        var data = new byte[]{OUTPUT_CODE_RGB, OUTPUT_CODE_RGB_RAINBOW, phase_shift, saturation, brightness, speed, reverse};
        if (priority) {
            handler.addToPriorityQueue(data);
        } else {
            handler.publishRGBUpdate(new byte[][]{data});
        }
    }

    public static void sendWave(String deviceSerialNumber, byte hue, byte saturation, byte brightness, byte speed, byte reverse, byte bounce, boolean priority) {
        var handler = DeviceScanner.CONNECTED_DEVICE_MAP.get(deviceSerialNumber);
        if (handler == null)
            throw new IllegalArgumentException("invalid device");
        var data = new byte[]{OUTPUT_CODE_RGB, OUTPUT_CODE_RGB_WAVE, hue, saturation, brightness, speed, reverse, bounce};
        if (priority) {
            handler.addToPriorityQueue(data);
        } else {
            handler.publishRGBUpdate(new byte[][]{data});
        }
    }

    public static void sendBreath(String deviceSerialNumber, byte hue, byte saturation, byte brightness, byte speed, boolean priority) {
        var handler = DeviceScanner.CONNECTED_DEVICE_MAP.get(deviceSerialNumber);
        if (handler == null)
            throw new IllegalArgumentException("invalid device");
        var data = new byte[]{OUTPUT_CODE_RGB, OUTPUT_CODE_RGB_BREATH, hue, saturation, brightness, speed};
        if (priority) {
            handler.addToPriorityQueue(data);
        } else {
            handler.publishRGBUpdate(new byte[][]{data});
        }
    }

    @SuppressWarnings("NumericCastThatLosesPrecision")
    public static void sendRGBAll(String deviceSerialNumber, Color color, boolean[] bs, boolean priority) {
        sendRGBAll(deviceSerialNumber, (int) (color.getRed() * MAX_BYTE), (int) (color.getGreen() * MAX_BYTE), (int) (color.getBlue() * MAX_BYTE), bs, priority);
    }

    public static void sendRGBAll(String deviceSerialNumber, int red, int green, int blue, boolean[] volumeTrack, boolean priority) {
        var handler = DeviceScanner.CONNECTED_DEVICE_MAP.get(deviceSerialNumber);
        if (handler == null)
            throw new IllegalArgumentException("invalid device");
        if (!isIntByteSize(red, green, blue))
            throw new IllegalArgumentException("ints must be byte size");
        var data = new ByteWriter(6 + volumeTrack.length)
                .append(OUTPUT_CODE_RGB, OUTPUT_CODE_RGB_RGB, 0)
                .append(red, green, blue);
        for (var b : volumeTrack)
            data.append(b ? 1 : 0);
        if (priority) {
            handler.addToPriorityQueue(data.get());
        } else {
            handler.publishRGBUpdate(new byte[][]{data.get()});
        }
    }

    private static boolean isIntByteSize(int... is) {
        return Arrays.stream(is).noneMatch(i -> i < 0 || i > MAX_BYTE);
    }
}
