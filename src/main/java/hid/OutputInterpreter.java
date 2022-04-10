package hid;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javafx.scene.paint.Color;
import main.DeviceType;
import save.LightingConfig;
import save.LightingConfig.LightingMode;
import save.SingleKnobLightingConfig;
import save.SingleKnobLightingConfig.SINGLE_KNOB_MODE;
import save.SingleLogoLightingConfig;
import save.SingleLogoLightingConfig.SINGLE_LOGO_MODE;
import save.SingleSliderLabelLightingConfig;
import save.SingleSliderLabelLightingConfig.SINGLE_SLIDER_LABEL_MODE;
import save.SingleSliderLightingConfig;
import save.SingleSliderLightingConfig.SINGLE_SLIDER_MODE;

public class OutputInterpreter {
    private static final byte[] OUTPUT_CODE_INIT = { 1 };

    private static final byte OUTPUT_CODE_RGB = 2;

    private static final byte OUTPUT_CODE_LED_MAX_CURRENT = 3;

    private static final byte OUTPUT_CODE_RGB_ALL_KNOBS = 0;

    private static final byte OUTPUT_CODE_RGB_SINGLE_KNOB = 1;

    private static final byte OUTPUT_CODE_RGB_FULL_DATA = 0;

    private static final byte OUTPUT_CODE_RGB_RGB = 1;

    private static final byte OUTPUT_CODE_RGB_HSV = 2;

    private static final byte OUTPUT_CODE_RGB_RAINBOW = 3;

    private static final byte OUTPUT_CODE_RGB_WAVE = 4;

    private static final byte OUTPUT_CODE_RGB_BREATH = 5;

    public static void sendInit(String deviceSerialNumber) {
        DeviceCommunicationHandler handler = DeviceScanner.CONNECTED_DEVICE_MAP.get(deviceSerialNumber);
        if (handler == null)
            throw new IllegalArgumentException("invalid device");
        handler.addToPriorityQueue(OUTPUT_CODE_INIT);
    }

    public static void sendLEDMaxCurrent(String deviceSerialNumber, int maxCurrent) {
        DeviceCommunicationHandler handler = DeviceScanner.CONNECTED_DEVICE_MAP.get(deviceSerialNumber);
        if (handler == null)
            throw new IllegalArgumentException("invalid device");
        if (maxCurrent < 0)
            throw new IllegalArgumentException("max current millamps cannot be negative");
        byte[] data = new byte[5];
        data[0] = 3;
        byte[] intData = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(maxCurrent).array();
        for (int i = 0; i < 4; ) {
            data[i + 1] = intData[i];
            i++;
        }
        handler.addToPriorityQueue(data);
    }

    public static void sendFullLEDData(String deviceSerialNumber, String[] colors, boolean[] volumeTrack, boolean priority) {
        DeviceCommunicationHandler handler = DeviceScanner.CONNECTED_DEVICE_MAP.get(deviceSerialNumber);
        if (handler == null)
            throw new IllegalArgumentException("invalid device");
        byte[] data = new byte[2 + 4 * colors.length + colors.length];
        data[0] = 2;
        data[1] = 0;
        int i;
        for (i = 0; i < colors.length; i++) {
            Color c = Color.valueOf(colors[i]);
            data[2 + 4 * i + 0] = 1;
            data[2 + 4 * i + 1] = (byte) c.getRed();
            data[2 + 4 * i + 2] = (byte) c.getGreen();
            data[2 + 4 * i + 3] = (byte) c.getBlue();
        }
        for (i = 0; i < volumeTrack.length; i++)
            data[2 + 4 * colors.length + i] = (byte) (volumeTrack[i] ? 1 : 0);
        if (priority) {
            handler.addToPriorityQueue(data);
        } else {
            handler.publishRGBUpdate(new byte[][] { data });
        }
    }

    public static void sendLightingConfig(String serialNumber, DeviceType dt, LightingConfig config, boolean priority) {
        if (dt == DeviceType.PCPANEL_RGB) {
            sendLightingConfigRGB(serialNumber, config, priority);
        } else if (dt == DeviceType.PCPANEL_MINI) {
            sendLightingConfigMini(serialNumber, config, priority);
        } else if (dt == DeviceType.PCPANEL_PRO) {
            sendLightingConfigPro(serialNumber, config, priority);
        } else {
            throw new IllegalArgumentException("unknown devicetype: " + dt.name());
        }
    }

    private static void sendLightingConfigMini(String serialNumber, LightingConfig config, boolean priority) {
        DeviceCommunicationHandler handler = DeviceScanner.CONNECTED_DEVICE_MAP.get(serialNumber);
        LightingMode mode = config.getLightingMode();
        if (mode == LightingMode.ALL_COLOR) {
            Color c1 = Color.valueOf(config.getAllColor());
            byte[] data = new byte[64];
            data[0] = 6;
            data[1] = 4;
            data[2] = 5;
            data[3] = (byte) c1.getRed();
            data[4] = (byte) c1.getGreen();
            data[5] = (byte) c1.getBlue();
            handler.sendMessage(priority, new byte[][] { data });
        } else if (mode == LightingMode.ALL_RAINBOW) {
            byte[] data = new byte[64];
            data[0] = 6;
            data[1] = 4;
            data[2] = (byte) ((config.getRainbowVertical() == 1) ? 2 : 1);
            data[3] = config.getRainbowPhaseShift();
            data[4] = -1;
            data[5] = config.getRainbowBrightness();
            data[6] = config.getRainbowSpeed();
            data[7] = config.getRainbowReverse();
            handler.sendMessage(priority, new byte[][] { data });
        } else if (mode == LightingMode.ALL_WAVE) {
            byte[] data = new byte[64];
            data[0] = 6;
            data[1] = 4;
            data[2] = 3;
            data[3] = config.getWaveHue();
            data[4] = -1;
            data[5] = config.getWaveBrightness();
            data[6] = config.getWaveSpeed();
            data[7] = config.getWaveReverse();
            data[8] = config.getWaveBounce();
            handler.sendMessage(priority, new byte[][] { data });
        } else if (mode == LightingMode.ALL_BREATH) {
            byte[] data = new byte[64];
            data[0] = 6;
            data[1] = 4;
            data[2] = 4;
            data[3] = config.getBreathHue();
            data[4] = -1;
            data[5] = config.getBreathBrightness();
            data[6] = config.getBreathSpeed();
            handler.sendMessage(priority, new byte[][] { data });
        } else if (mode == LightingMode.CUSTOM) {
            SingleKnobLightingConfig[] knobConfigs = config.getKnobConfigs();
            byte[] knobData = new byte[64];
            knobData[0] = 6;
            knobData[1] = 2;
            for (int i = 0; i < knobConfigs.length; i++) {
                if (knobConfigs[i].getMode() == SINGLE_KNOB_MODE.STATIC) {
                    Color c1 = Color.valueOf(knobConfigs[i].getColor1());
                    knobData[2 + 7 * i] = 1;
                    knobData[2 + 7 * i + 1] = (byte) c1.getRed();
                    knobData[2 + 7 * i + 2] = (byte) c1.getGreen();
                    knobData[2 + 7 * i + 3] = (byte) c1.getBlue();
                } else if (knobConfigs[i].getMode() == SINGLE_KNOB_MODE.VOLUME_GRADIENT) {
                    Color c1 = Color.valueOf(knobConfigs[i].getColor1());
                    Color c2 = Color.valueOf(knobConfigs[i].getColor2());
                    knobData[2 + 7 * i] = 2;
                    knobData[2 + 7 * i + 1] = (byte) c1.getRed();
                    knobData[2 + 7 * i + 2] = (byte) c1.getGreen();
                    knobData[2 + 7 * i + 3] = (byte) c1.getBlue();
                    knobData[2 + 7 * i + 4] = (byte) c2.getRed();
                    knobData[2 + 7 * i + 5] = (byte) c2.getGreen();
                    knobData[2 + 7 * i + 6] = (byte) c2.getBlue();
                }
            }
            handler.sendMessage(priority, new byte[][] { knobData });
        }
    }

    private static void sendLightingConfigPro(String serialNumber, LightingConfig config, boolean priority) {
        DeviceCommunicationHandler handler = DeviceScanner.CONNECTED_DEVICE_MAP.get(serialNumber);
        LightingMode mode = config.getLightingMode();
        if (mode == LightingMode.ALL_COLOR) {
            Color c1 = Color.valueOf(config.getAllColor());
            byte[] data = new byte[64];
            data[0] = 5;
            data[1] = 4;
            data[2] = 2;
            data[3] = (byte) c1.getRed();
            data[4] = (byte) c1.getGreen();
            data[5] = (byte) c1.getBlue();
            handler.sendMessage(priority, new byte[][] { data });
        } else if (mode == LightingMode.ALL_RAINBOW) {
            byte[] data = new byte[64];
            data[0] = 5;
            data[1] = 4;
            data[2] = 1;
            data[3] = config.getRainbowPhaseShift();
            data[4] = -1;
            data[5] = config.getRainbowBrightness();
            data[6] = config.getRainbowSpeed();
            data[7] = config.getRainbowReverse();
            handler.sendMessage(priority, new byte[][] { data });
        } else if (mode == LightingMode.ALL_WAVE) {
            byte[] data = new byte[64];
            data[0] = 5;
            data[1] = 4;
            data[2] = 3;
            data[3] = config.getWaveHue();
            data[4] = -1;
            data[5] = config.getWaveBrightness();
            data[6] = config.getWaveSpeed();
            data[7] = config.getWaveReverse();
            data[8] = config.getWaveBounce();
            handler.sendMessage(priority, new byte[][] { data });
        } else if (mode == LightingMode.ALL_BREATH) {
            byte[] data = new byte[64];
            data[0] = 5;
            data[1] = 4;
            data[2] = 4;
            data[3] = config.getBreathHue();
            data[4] = -1;
            data[5] = config.getBreathBrightness();
            data[6] = config.getBreathSpeed();
            handler.sendMessage(priority, new byte[][] { data });
        } else if (mode == LightingMode.CUSTOM) {
            SingleKnobLightingConfig[] knobConfigs = config.getKnobConfigs();
            SingleSliderLabelLightingConfig[] sliderLabelConfigs = config.getSliderLabelConfigs();
            SingleSliderLightingConfig[] sliderConfigs = config.getSliderConfigs();
            SingleLogoLightingConfig logoConfig = config.getLogoConfig();
            byte[] knobData = new byte[64];
            knobData[0] = 5;
            knobData[1] = 2;
            for (int i = 0; i < knobConfigs.length; i++) {
                if (knobConfigs[i].getMode() == SINGLE_KNOB_MODE.STATIC) {
                    Color c1 = Color.valueOf(knobConfigs[i].getColor1());
                    knobData[2 + 7 * i] = 1;
                    knobData[2 + 7 * i + 1] = (byte) c1.getRed();
                    knobData[2 + 7 * i + 2] = (byte) c1.getGreen();
                    knobData[2 + 7 * i + 3] = (byte) c1.getBlue();
                } else if (knobConfigs[i].getMode() == SINGLE_KNOB_MODE.VOLUME_GRADIENT) {
                    Color c1 = Color.valueOf(knobConfigs[i].getColor1());
                    Color c2 = Color.valueOf(knobConfigs[i].getColor2());
                    knobData[2 + 7 * i] = 2;
                    knobData[2 + 7 * i + 1] = (byte) c1.getRed();
                    knobData[2 + 7 * i + 2] = (byte) c1.getGreen();
                    knobData[2 + 7 * i + 3] = (byte) c1.getBlue();
                    knobData[2 + 7 * i + 4] = (byte) c2.getRed();
                    knobData[2 + 7 * i + 5] = (byte) c2.getGreen();
                    knobData[2 + 7 * i + 6] = (byte) c2.getBlue();
                }
            }
            byte[] sliderLabelData = new byte[64];
            sliderLabelData[0] = 5;
            sliderLabelData[1] = 1;
            for (int j = 0; j < sliderLabelConfigs.length; j++) {
                if (sliderLabelConfigs[j].getMode() == SINGLE_SLIDER_LABEL_MODE.STATIC) {
                    Color c1 = Color.valueOf(sliderLabelConfigs[j].getColor());
                    sliderLabelData[2 + 7 * j] = 1;
                    sliderLabelData[2 + 7 * j + 1] = (byte) c1.getRed();
                    sliderLabelData[2 + 7 * j + 2] = (byte) c1.getGreen();
                    sliderLabelData[2 + 7 * j + 3] = (byte) c1.getBlue();
                }
            }
            byte[] sliderData = new byte[64];
            sliderData[0] = 5;
            sliderData[1] = 0;
            for (int k = 0; k < sliderConfigs.length; k++) {
                if (sliderConfigs[k].getMode() == SINGLE_SLIDER_MODE.STATIC) {
                    Color c1 = Color.valueOf(sliderConfigs[k].getColor1());
                    sliderData[2 + 7 * k] = 1;
                    sliderData[2 + 7 * k + 1] = (byte) c1.getRed();
                    sliderData[2 + 7 * k + 2] = (byte) c1.getGreen();
                    sliderData[2 + 7 * k + 3] = (byte) c1.getBlue();
                    sliderData[2 + 7 * k + 4] = (byte) c1.getRed();
                    sliderData[2 + 7 * k + 5] = (byte) c1.getGreen();
                    sliderData[2 + 7 * k + 6] = (byte) c1.getBlue();
                } else if (sliderConfigs[k].getMode() == SINGLE_SLIDER_MODE.STATIC_GRADIENT) {
                    Color c1 = Color.valueOf(sliderConfigs[k].getColor1());
                    Color c2 = Color.valueOf(sliderConfigs[k].getColor2());
                    sliderData[2 + 7 * k] = 1;
                    sliderData[2 + 7 * k + 1] = (byte) c1.getRed();
                    sliderData[2 + 7 * k + 2] = (byte) c1.getGreen();
                    sliderData[2 + 7 * k + 3] = (byte) c1.getBlue();
                    sliderData[2 + 7 * k + 4] = (byte) c2.getRed();
                    sliderData[2 + 7 * k + 5] = (byte) c2.getGreen();
                    sliderData[2 + 7 * k + 6] = (byte) c2.getBlue();
                } else if (sliderConfigs[k].getMode() == SINGLE_SLIDER_MODE.VOLUME_GRADIENT) {
                    Color c1 = Color.valueOf(sliderConfigs[k].getColor1());
                    Color c2 = Color.valueOf(sliderConfigs[k].getColor2());
                    sliderData[2 + 7 * k] = 3;
                    sliderData[2 + 7 * k + 1] = (byte) c1.getRed();
                    sliderData[2 + 7 * k + 2] = (byte) c1.getGreen();
                    sliderData[2 + 7 * k + 3] = (byte) c1.getBlue();
                    sliderData[2 + 7 * k + 4] = (byte) c2.getRed();
                    sliderData[2 + 7 * k + 5] = (byte) c2.getGreen();
                    sliderData[2 + 7 * k + 6] = (byte) c2.getBlue();
                }
            }
            byte[] logoData = new byte[64];
            logoData[0] = 5;
            logoData[1] = 3;
            if (logoConfig.getMode() == SINGLE_LOGO_MODE.STATIC) {
                Color c1 = Color.valueOf(logoConfig.getColor());
                logoData[2] = 1;
                logoData[3] = (byte) c1.getRed();
                logoData[4] = (byte) c1.getGreen();
                logoData[5] = (byte) c1.getBlue();
            } else if (logoConfig.getMode() == SINGLE_LOGO_MODE.RAINBOW) {
                logoData[2] = 2;
                logoData[3] = -1;
                logoData[4] = logoConfig.getBrightness();
                logoData[5] = logoConfig.getSpeed();
            } else if (logoConfig.getMode() == SINGLE_LOGO_MODE.BREATH) {
                logoData[2] = 3;
                logoData[3] = logoConfig.getHue();
                logoData[4] = -1;
                logoData[5] = logoConfig.getBrightness();
                logoData[6] = logoConfig.getSpeed();
            }
            handler.sendMessage(priority, knobData, sliderLabelData, sliderData, logoData);
        }
    }

    private static void sendLightingConfigRGB(String serialNumber, LightingConfig config, boolean priority) {
        LightingMode mode = config.getLightingMode();
        if (mode == LightingMode.ALL_COLOR) {
            sendRGBAll(serialNumber, Color.valueOf(config.getAllColor()), config.getVolumeBrightnessTrackingEnabled(), priority);
        } else if (mode == LightingMode.SINGLE_COLOR) {
            sendFullLEDData(serialNumber, config.getIndividualColors(), config.getVolumeBrightnessTrackingEnabled(), priority);
        } else if (mode == LightingMode.ALL_RAINBOW) {
            sendRainbow(serialNumber, config.getRainbowPhaseShift(), (byte) -1, config.getRainbowBrightness(), config.getRainbowSpeed(), config.getRainbowReverse(), priority);
        } else if (mode == LightingMode.ALL_WAVE) {
            sendWave(serialNumber, config.getWaveHue(), (byte) -1, config.getWaveBrightness(), config.getWaveSpeed(), config.getWaveReverse(), config.getWaveBounce(), priority);
        } else if (mode == LightingMode.ALL_BREATH) {
            sendBreath(serialNumber, config.getBreathHue(), (byte) -1, config.getBreathBrightness(), config.getBreathSpeed(), priority);
        } else {
            System.err.println("unexpected lighting mode in deviceOutputHandler");
        }
    }

    public static void sendRainbow(String deviceSerialNumber, byte phase_shift, byte saturation, byte brightness, byte speed, byte reverse, boolean priority) {
        DeviceCommunicationHandler handler = DeviceScanner.CONNECTED_DEVICE_MAP.get(deviceSerialNumber);
        if (handler == null)
            throw new IllegalArgumentException("invalid device");
        byte[] data = { 2, 3, phase_shift, saturation, brightness, speed, reverse };
        if (priority) {
            handler.addToPriorityQueue(data);
        } else {
            handler.publishRGBUpdate(new byte[][] { data });
        }
    }

    public static void sendWave(String deviceSerialNumber, byte hue, byte saturation, byte brightness, byte speed, byte reverse, byte bounce, boolean priority) {
        DeviceCommunicationHandler handler = DeviceScanner.CONNECTED_DEVICE_MAP.get(deviceSerialNumber);
        if (handler == null)
            throw new IllegalArgumentException("invalid device");
        byte[] data = { 2, 4, hue, saturation, brightness, speed, reverse, bounce };
        if (priority) {
            handler.addToPriorityQueue(data);
        } else {
            handler.publishRGBUpdate(new byte[][] { data });
        }
    }

    public static void sendBreath(String deviceSerialNumber, byte hue, byte saturation, byte brightness, byte speed, boolean priority) {
        DeviceCommunicationHandler handler = DeviceScanner.CONNECTED_DEVICE_MAP.get(deviceSerialNumber);
        if (handler == null)
            throw new IllegalArgumentException("invalid device");
        byte[] data = { 2, 5, hue, saturation, brightness, speed };
        if (priority) {
            handler.addToPriorityQueue(data);
        } else {
            handler.publishRGBUpdate(new byte[][] { data });
        }
    }

    public static void sendRGB(String deviceSerialNumber, int knob, Color color) {
        sendRGB(deviceSerialNumber, knob, (int) (color.getRed() * 255.0D), (int) (color.getGreen() * 255.0D), (int) (color.getBlue() * 255.0D));
    }

    public static void sendRGB(String deviceSerialNumber, int knob, int red, int green, int blue) {
        DeviceCommunicationHandler handler = DeviceScanner.CONNECTED_DEVICE_MAP.get(deviceSerialNumber);
        if (handler == null)
            throw new IllegalArgumentException("invalid device");
        if (!isIntByteSize(knob, red, green, blue))
            throw new IllegalArgumentException("ints must be byte size");
        byte[] data = { 2, 1, 1, (byte) knob, (byte) red, (byte) green, (byte) blue };
        handler.publishRGBUpdate(new byte[][] { data });
    }

    public static void sendRGBAll(String deviceSerialNumber, Color color, boolean[] bs, boolean priority) {
        sendRGBAll(deviceSerialNumber, (int) (color.getRed() * 255.0D), (int) (color.getGreen() * 255.0D), (int) (color.getBlue() * 255.0D), bs, priority);
    }

    public static void sendRGBAll(String deviceSerialNumber, int red, int green, int blue, boolean[] volumeTrack, boolean priority) {
        DeviceCommunicationHandler handler = DeviceScanner.CONNECTED_DEVICE_MAP.get(deviceSerialNumber);
        if (handler == null)
            throw new IllegalArgumentException("invalid device");
        if (!isIntByteSize(red, green, blue))
            throw new IllegalArgumentException("ints must be byte size");
        byte[] data = new byte[6 + volumeTrack.length];
        data[0] = 2;
        data[1] = 1;
        data[2] = 0;
        data[3] = (byte) red;
        data[4] = (byte) green;
        data[5] = (byte) blue;
        for (int i = 0; i < volumeTrack.length; i++)
            data[6 + i] = (byte) (volumeTrack[i] ? 1 : 0);
        if (priority) {
            handler.addToPriorityQueue(data);
        } else {
            handler.publishRGBUpdate(new byte[][] { data });
        }
    }

    public static void sendHSV(String deviceSerialNumber, int knob, int hue, int saturation, int brightness) {
        DeviceCommunicationHandler handler = DeviceScanner.CONNECTED_DEVICE_MAP.get(deviceSerialNumber);
        if (handler == null)
            throw new IllegalArgumentException("invalid device");
        if (!isIntByteSize(knob, hue, saturation, brightness))
            throw new IllegalArgumentException("ints must be byte size");
        byte[] data = { 2, 2, 1, (byte) knob, (byte) hue, (byte) saturation, (byte) brightness };
        handler.publishRGBUpdate(new byte[][] { data });
    }

    public static void sendHSVAll(String deviceSerialNumber, int hue, int saturation, int brightness) {
        DeviceCommunicationHandler handler = DeviceScanner.CONNECTED_DEVICE_MAP.get(deviceSerialNumber);
        if (handler == null)
            throw new IllegalArgumentException("null serial number");
        if (!isIntByteSize(hue, saturation, brightness))
            throw new IllegalArgumentException("ints must be byte size");
        byte[] data = { 2, 2, (byte) hue, (byte) saturation, (byte) brightness };
        handler.publishRGBUpdate(new byte[][] { data });
    }

    private static boolean isIntByteSize(int... is) {
        byte b;
        int i;
        int[] arrayOfInt;
        for (i = (arrayOfInt = is).length, b = 0; b < i; ) {
            int j = arrayOfInt[b];
            if (!isIntByteSize(j))
                return false;
            b++;
        }
        return true;
    }

    private static boolean isIntByteSize(int i) {
        return i >= 0 && i < 256;
    }
}
