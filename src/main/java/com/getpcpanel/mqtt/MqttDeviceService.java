package com.getpcpanel.mqtt;

import static com.getpcpanel.mqtt.MqttService.ORDER_OF_SAVE;
import static com.getpcpanel.mqtt.MqttTopicHelper.ActionType.button;
import static com.getpcpanel.mqtt.MqttTopicHelper.ColorType.dial;
import static com.getpcpanel.mqtt.MqttTopicHelper.ColorType.label;
import static com.getpcpanel.mqtt.MqttTopicHelper.ColorType.logo;
import static com.getpcpanel.mqtt.MqttTopicHelper.ColorType.slider;
import static com.getpcpanel.mqtt.MqttTopicHelper.ValueType.analog;
import static com.getpcpanel.mqtt.MqttTopicHelper.ValueType.brightness;

import java.util.function.Consumer;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.util.TriConsumer;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import com.getpcpanel.device.Device;
import com.getpcpanel.hid.DeviceCommunicationHandler;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.hid.DeviceHolder.DeviceFullyConnectedEvent;
import com.getpcpanel.mqtt.MqttTopicHelper.ColorType;
import com.getpcpanel.mqtt.MqttTopicHelper.DeviceMqttTopicHelper;
import com.getpcpanel.profile.LightingConfig;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.SingleKnobLightingConfig;
import com.getpcpanel.profile.SingleKnobLightingConfig.SINGLE_KNOB_MODE;
import com.getpcpanel.profile.SingleLogoLightingConfig;
import com.getpcpanel.profile.SingleLogoLightingConfig.SINGLE_LOGO_MODE;
import com.getpcpanel.profile.SingleSliderLabelLightingConfig;
import com.getpcpanel.profile.SingleSliderLabelLightingConfig.SINGLE_SLIDER_LABEL_MODE;
import com.getpcpanel.profile.SingleSliderLightingConfig;
import com.getpcpanel.profile.SingleSliderLightingConfig.SINGLE_SLIDER_MODE;
import com.getpcpanel.util.coloroverride.ColorOverrideHolder;
import com.getpcpanel.util.coloroverride.IOverrideColorProvider;
import com.getpcpanel.util.coloroverride.IOverrideColorProviderProvider;

import javafx.scene.paint.Color;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.EntryStream;

@Log4j2
@Service
@Order(1)
@RequiredArgsConstructor
public class MqttDeviceService implements IOverrideColorProviderProvider {
    private final MqttService mqtt;
    private final SaveService saveService;
    private final DeviceHolder deviceHolder;
    private final MqttHomeAssistantHelper mqttHomeAssistantHelper;
    private final MqttTopicHelper mqttTopicHelper;
    private final ColorOverrideHolder colorOverrideHolder = new MqttColorOverrideHolder();

    @Order(ORDER_OF_SAVE + 1) // Ensure we are disconnected if the setting is turned off
    @EventListener(SaveService.SaveEvent.class)
    public void saveChanged() {
        if (mqtt.isConnected()) {
            initialize();
        }
    }

    @EventListener
    public void mqttConnected(MqttStatusEvent event) {
        if (!event.connected()) {
            return;
        }

        log.trace("Setting up mqtt");
        initialize();
    }

    private void initialize() {
        deviceHolder.all().forEach(this::initialize);
    }

    public boolean clear() {
        if (mqtt.isConnected()) {
            deviceHolder.all().forEach(this::clear);
            return true;
        }
        return false;
    }

    @EventListener
    public void deviceConnected(DeviceFullyConnectedEvent event) {
        if (!mqtt.isConnected()) {
            return;
        }
        initialize(event.device());
    }

    @EventListener
    public void dialAction(DeviceCommunicationHandler.KnobRotateEvent dial) {
        if (!mqtt.isConnected()) {
            return;
        }

        saveService.getProfile(dial.serialNum()).ifPresent(profile -> {
            var topic = mqttTopicHelper.valueTopic(dial.serialNum(), analog, dial.knob());
            mqtt.send(topic, String.valueOf(dial.value()), false);
        });
    }

    @EventListener
    public void buttonPress(DeviceCommunicationHandler.ButtonPressEvent btn) {
        saveService.getProfile(btn.serialNum()).ifPresent(profile -> {
            var topic = mqttTopicHelper.actionTopic(btn.serialNum(), button, btn.button());
            mqtt.send(topic, btn.pressed() ? "click" : "release", true);
        });
    }

    private void initialize(Device device) {
        var lighting = device.getLightingConfig();
        if (lighting.getLightingMode() != LightingConfig.LightingMode.CUSTOM) {
            log.debug("Only custom lighting will be written to mqtt");
            return;
        }

        writeLighting(device, lighting);
        buildSubscriptions(device, lighting);

        if (saveService.get().getMqtt().homeAssistant().enableDiscovery()) {
            mqttHomeAssistantHelper.discover(saveService.get().getMqtt(), device);
        }
    }

    private void clear(Device device) {
        var baseTopic = mqttTopicHelper.baseTopicFilter();
        mqtt.removeAll(baseTopic);
        mqttHomeAssistantHelper.clearAll(saveService.get().getMqtt());
    }

    private void buildSubscriptions(Device device, LightingConfig lighting) {
        var topicHelper = mqttTopicHelper.device(device.getSerialNumber());
        Runnable andThen = () -> device.setLighting(lighting, true);

        TriConsumer<Integer, String, SingleKnobLightingConfig> knobOverride = (idx, payload, knob) ->
                colorOverrideHolder.setDialOverride(device.getSerialNumber(), idx, new SingleKnobLightingConfig().setMode(SINGLE_KNOB_MODE.STATIC).setColor1(payload));
        TriConsumer<Integer, String, SingleSliderLightingConfig> sliderOverride = (idx, payload, knob) ->
                colorOverrideHolder.setSliderOverride(device.getSerialNumber(), idx, new SingleSliderLightingConfig().setMode(SINGLE_SLIDER_MODE.STATIC).setColor1(payload));
        TriConsumer<Integer, String, SingleSliderLabelLightingConfig> sliderLabelOverride = (idx, payload, knob) ->
                colorOverrideHolder.setSliderLabelOverride(device.getSerialNumber(), idx, new SingleSliderLabelLightingConfig().setMode(SINGLE_SLIDER_LABEL_MODE.STATIC).setColor(payload));
        Consumer<String> logoOverride = payload ->
                colorOverrideHolder.setLogoOverride(device.getSerialNumber(), new SingleLogoLightingConfig().setMode(SINGLE_LOGO_MODE.STATIC).setColor(payload));

        subscribeTo(topicHelper.valueTopic(brightness, 0), payload -> lighting.setGlobalBrightness(NumberUtils.toInt(payload, 100)), andThen);
        subscribeToColor(lighting.getKnobConfigs(), topicHelper, dial, knobOverride, andThen);
        subscribeToColor(lighting.getSliderConfigs(), topicHelper, slider, sliderOverride, andThen);
        subscribeToColor(lighting.getSliderLabelConfigs(), topicHelper, label, sliderLabelOverride, andThen);
        if (lighting.getLogoConfig() != null) {
            subscribeTo(topicHelper.lightTopic(logo, 0), ifValidColor(logoOverride), andThen);
        }
    }

    private Consumer<String> ifValidColor(Consumer<String> toRun) {
        return color -> {
            try {
                Color.valueOf(color);
                toRun.accept(color);
            } catch (Exception e) {
                log.debug("Invalid color: {}", color);
            }
        };
    }

    private <T> void subscribeToColor(T[] items, DeviceMqttTopicHelper topicHelper, ColorType type, TriConsumer<Integer, String, T> consumer, Runnable andThen) {
        EntryStream.of(items).forKeyValue((idx, knob) -> {
            var topic = topicHelper.lightTopic(type, idx);
            subscribeTo(topic, ifValidColor(payload -> consumer.accept(idx, payload, knob)), andThen);
        });
    }

    private void subscribeTo(String topic, Consumer<String> consumer, Runnable andThen) {
        mqtt.subscribeString(topic, publish -> {
            consumer.accept(publish);
            andThen.run();
        });
    }

    private void writeLighting(Device device, LightingConfig lighting) {
        var mqttHelper = mqttTopicHelper.device(device.getSerialNumber());

        mqtt.send(mqttHelper.valueTopic(brightness, 0), String.valueOf(lighting.getGlobalBrightness()), false);
        sendColors(lighting.getKnobConfigs(), mqttHelper, dial, SingleKnobLightingConfig::getColor1);
        sendColors(lighting.getSliderConfigs(), mqttHelper, slider, SingleSliderLightingConfig::getColor1);
        sendColors(lighting.getSliderLabelConfigs(), mqttHelper, label, SingleSliderLabelLightingConfig::getColor);
        if (device.getDeviceType().isHasLogoLed() && lighting.getLogoConfig() != null) {
            mqtt.send(mqttHelper.lightTopic(logo, 0), toColorString(lighting.getLogoConfig().getColor()), false);
        }
    }

    private <T> void sendColors(T[] items, DeviceMqttTopicHelper mqttHelper, ColorType colorType, Function<T, String> colorMapper) {
        EntryStream.of(items).forKeyValue((idx, knob) -> {
            var sliderTopic = mqttHelper.lightTopic(colorType, idx);
            mqtt.send(sliderTopic, toColorString(colorMapper.apply(knob)), false);
        });
    }

    private String toColorString(String color) {
        return color == null ? "000000" : color;
    }

    @Override
    public IOverrideColorProvider getOverrideColorProvider() {
        return colorOverrideHolder;
    }

    private static class MqttColorOverrideHolder extends ColorOverrideHolder {
        public static final String OFF = "#000000";

        @Override
        public void setDialOverride(String deviceSerial, int dial, @Nullable SingleKnobLightingConfig config) {
            if (config == null || config.getColor1().equals(OFF))
                super.setDialOverride(deviceSerial, dial, null);
            else
                super.setDialOverride(deviceSerial, dial, config);
        }

        @Override
        public void setSliderOverride(String deviceSerial, int slider, @Nullable SingleSliderLightingConfig config) {
            if (config == null || config.getColor1().equals(OFF))
                super.setSliderOverride(deviceSerial, slider, null);
            else
                super.setSliderOverride(deviceSerial, slider, config);
        }

        @Override
        public void setSliderLabelOverride(String deviceSerial, int slider, @Nullable SingleSliderLabelLightingConfig config) {
            if (config == null || config.getColor().equals(OFF))
                super.setSliderLabelOverride(deviceSerial, slider, null);
            else
                super.setSliderLabelOverride(deviceSerial, slider, config);
        }

        @Override
        public void setLogoOverride(String deviceSerial, @Nullable SingleLogoLightingConfig config) {
            if (config == null || config.getColor().equals(OFF))
                super.setLogoOverride(deviceSerial, null);
            else
                super.setLogoOverride(deviceSerial, config);
        }

    }
}
