package com.getpcpanel.mqtt;

import static com.getpcpanel.mqtt.MqttTopicHelper.ColorType.dial;
import static com.getpcpanel.mqtt.MqttTopicHelper.ColorType.label;
import static com.getpcpanel.mqtt.MqttTopicHelper.ColorType.logo;
import static com.getpcpanel.mqtt.MqttTopicHelper.ColorType.slider;
import static com.getpcpanel.mqtt.MqttTopicHelper.ValueType.brightness;
import static com.getpcpanel.util.Util.parseColor;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.util.TriConsumer;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import com.getpcpanel.device.Device;
import com.getpcpanel.profile.LightingConfig;
import com.getpcpanel.profile.SingleKnobLightingConfig;
import com.getpcpanel.profile.SingleLogoLightingConfig;
import com.getpcpanel.profile.SingleSliderLabelLightingConfig;
import com.getpcpanel.profile.SingleSliderLightingConfig;
import com.getpcpanel.util.coloroverride.ColorOverrideHolder;
import com.getpcpanel.util.coloroverride.IOverrideColorProvider;
import com.getpcpanel.util.coloroverride.IOverrideColorProviderProvider;

import javafx.scene.paint.Color;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;

@Log4j2
@Service
@Order(1)
@RequiredArgsConstructor
public class MqttDeviceColorService implements IOverrideColorProviderProvider {
    public static final String EFFECT_NONE = "none";
    public static final String EFFECT_STOP_OVERRIDE = "stop_override";

    private final MqttService mqtt;
    private final MqttTopicHelper mqttTopicHelper;
    private final ColorOverrideHolder colorOverrideHolder = new MqttColorOverrideHolder();

    @Override
    public IOverrideColorProvider getOverrideColorProvider() {
        return colorOverrideHolder;
    }

    public void sendColor(String topic, @Nonnull String colorString, boolean immediate) {
        mqtt.send(topic, colorString, immediate);

        var setColor = parseColor(colorString).orElse(Color.BLACK);
        var r = Math.round(setColor.getRed() * 255);
        var g = Math.round(setColor.getGreen() * 255);
        var b = Math.round(setColor.getBlue() * 255);

        // Home assistant seems to think that the brightness is the highest value of the RGB
        var brightness = Math.max(r, Math.max(g, b));
        mqtt.send(topic + "/brightness", brightness, immediate, true);

        // Write the RGB value to the full-bright color
        if (brightness != 0) {
            r = r * 255 / brightness;
            g = g * 255 / brightness;
            b = b * 255 / brightness;
        }
        mqtt.send(topic + "/rgb", "%d,%d,%d".formatted(r, g, b), immediate, true);
    }

    public void buildSubscriptions(Device device, LightingConfig lighting) {
        var topicHelper = mqttTopicHelper.device(device.getSerialNumber());
        Runnable andThen = () -> device.setLighting(lighting, true);
        TriConsumer<Integer, String, SingleKnobLightingConfig> knobOverride = (idx, payload, knob) -> {
            colorOverrideHolder.setDialOverride(device.getSerialNumber(), idx, new SingleKnobLightingConfig().setMode(SingleKnobLightingConfig.SINGLE_KNOB_MODE.STATIC).setColor1(payload));
            andThen.run();
        };
        TriConsumer<Integer, String, SingleSliderLightingConfig> sliderOverride = (idx, payload, knob) -> {
            colorOverrideHolder.setSliderOverride(device.getSerialNumber(), idx, new SingleSliderLightingConfig().setMode(SingleSliderLightingConfig.SINGLE_SLIDER_MODE.STATIC).setColor1(payload));
            andThen.run();
        };
        TriConsumer<Integer, String, SingleSliderLabelLightingConfig> sliderLabelOverride = (idx, payload, knob) -> {
            colorOverrideHolder.setSliderLabelOverride(device.getSerialNumber(), idx, new SingleSliderLabelLightingConfig().setMode(SingleSliderLabelLightingConfig.SINGLE_SLIDER_LABEL_MODE.STATIC).setColor(payload));
            andThen.run();
        };
        Consumer<String> logoOverride = payload -> {
            colorOverrideHolder.setLogoOverride(device.getSerialNumber(), new SingleLogoLightingConfig().setMode(SingleLogoLightingConfig.SINGLE_LOGO_MODE.STATIC).setColor(payload));
            andThen.run();
        };

        subscribeTo(topicHelper.valueTopic(brightness, 0), payload -> lighting.setGlobalBrightness(NumberUtils.toInt(payload, 100)));
        subscribeToColors(lighting.getKnobConfigs(), topicHelper, dial, knobOverride, idx -> device.getLightingConfig().getKnobConfigs()[idx].getColor1());
        subscribeToColors(lighting.getSliderConfigs(), topicHelper, slider, sliderOverride, idx -> device.getLightingConfig().getSliderConfigs()[idx].getColor1());
        subscribeToColors(lighting.getSliderLabelConfigs(), topicHelper, label, sliderLabelOverride, idx -> device.getLightingConfig().getSliderLabelConfigs()[idx].getColor());
        if (lighting.getLogoConfig() != null) {
            subscribeToColor(topicHelper.lightTopic(logo, 0), logoOverride, () -> device.getLightingConfig().getLogoConfig().getColor());
        }
    }

    private <T> void subscribeToColors(T[] items, MqttTopicHelper.DeviceMqttTopicHelper topicHelper, MqttTopicHelper.ColorType type, TriConsumer<Integer, String, T> consumer, Function<Integer, String> currentColorSupplier) {
        EntryStream.of(items).forKeyValue((idx, knob) -> {
            var topic = topicHelper.lightTopic(type, idx);
            subscribeToColor(topic, payload -> consumer.accept(idx, payload, knob), () -> currentColorSupplier.apply(idx));
        });
    }

    private void subscribeTo(String topic, Consumer<String> consumer) {
        mqtt.subscribeString(topic, consumer);
    }

    private void subscribeToColor(String baseTopic, Consumer<String> colorOverrider, Supplier<String> currentColorSupplier) {
        var color = new MutableColor();

        // Base color changes
        mqtt.subscribeString(baseTopic, publish -> {
            log.debug("Color changed {}: {}", baseTopic, publish);
            var setColor = parseColor(publish);
            if (setColor.isEmpty()) {
                log.debug("Invalid color {}, stop overriding", publish);
                colorOverrider.accept(null);
                return;
            }

            color.fromColor(setColor.get());
            colorOverrider.accept(publish);
        });

        addHomeAssistantSubscriptions(baseTopic, colorOverrider, color, currentColorSupplier);
    }

    private void addHomeAssistantSubscriptions(String baseTopic, Consumer<String> colorOverrider, MutableColor color, Supplier<String> currentColorSupplier) {
        // On/off command
        mqtt.subscribeString(baseTopic + "/cmd", publish -> {
            log.debug("Command {}: {}", baseTopic, publish);
            switch (StringUtils.defaultString(StringUtils.lowerCase(publish))) {
                case "off" -> colorOverrider.accept("#000000");
                case "on" -> {
                    if (color.isOverriding) {
                        if (color.brightness == 0) {
                            color.brightness = 255;
                        }
                        var colorString = color.toColorString();
                        sendColor(baseTopic, colorString, false);
                        colorOverrider.accept(colorString);
                    }
                }
                default -> log.error("Unknown command {}", publish);
            }
        });

        // Brightness changes
        mqtt.subscribeString(baseTopic + "/brightness", publish -> {
            log.debug("Brightness changed {}: {}", baseTopic, publish);
            color.brightness = NumberUtils.toInt(publish, 255);
            color.isOverriding = true;
        });

        // Set rgb color
        mqtt.subscribeString(baseTopic + "/rgb", publish -> {
            log.debug("Rgb changed {}: {}", baseTopic, publish);

            var rgb = StreamEx.split(publish, ",").mapToInt(NumberUtils::toInt).toArray();
            if (rgb.length != 3) {
                log.error("Invalid RGB {}, ignoring", publish);
                return;
            }
            color.red = rgb[0];
            color.green = rgb[1];
            color.blue = rgb[2];
            color.isOverriding = true;
        });

        // Effect (stop overriding)
        mqtt.subscribeString(baseTopic + "/effect", publish -> {
            log.debug("Effect {}: {}", baseTopic, publish);
            if (EFFECT_STOP_OVERRIDE.equals(publish)) {
                colorOverrider.accept(null);
                color.isOverriding = false;
                sendColor(baseTopic, currentColorSupplier.get(), true);
                mqtt.send(baseTopic + "/effect", EFFECT_NONE, false);
            }
        });
    }

    private static class MqttColorOverrideHolder extends ColorOverrideHolder {
        @Override
        public void setDialOverride(String deviceSerial, int dial, @Nullable SingleKnobLightingConfig config) {
            if (config == null || config.getColor1() == null)
                super.setDialOverride(deviceSerial, dial, null);
            else
                super.setDialOverride(deviceSerial, dial, config);
        }

        @Override
        public void setSliderOverride(String deviceSerial, int slider, @Nullable SingleSliderLightingConfig config) {
            if (config == null || config.getColor1() == null)
                super.setSliderOverride(deviceSerial, slider, null);
            else
                super.setSliderOverride(deviceSerial, slider, config);
        }

        @Override
        public void setSliderLabelOverride(String deviceSerial, int slider, @Nullable SingleSliderLabelLightingConfig config) {
            if (config == null || config.getColor() == null)
                super.setSliderLabelOverride(deviceSerial, slider, null);
            else
                super.setSliderLabelOverride(deviceSerial, slider, config);
        }

        @Override
        public void setLogoOverride(String deviceSerial, @Nullable SingleLogoLightingConfig config) {
            if (config == null || config.getColor() == null)
                super.setLogoOverride(deviceSerial, null);
            else
                super.setLogoOverride(deviceSerial, config);
        }
    }

    private static class MutableColor {
        boolean isOverriding;
        int red = 255;
        int green = 255;
        int blue = 255;
        int brightness = 255;

        @SuppressWarnings("NumericCastThatLosesPrecision")
        public void fromColor(Color color) {
            red = (int) Math.round(color.getRed() * 255);
            green = (int) Math.round(color.getGreen() * 255);
            blue = (int) Math.round(color.getBlue() * 255);
            brightness = 255;
        }

        public String toColorString() {
            return String.format("#%02x%02x%02x", red * brightness / 255, green * brightness / 255, blue * brightness / 255);
        }
    }
}
