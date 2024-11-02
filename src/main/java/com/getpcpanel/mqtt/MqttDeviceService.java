package com.getpcpanel.mqtt;

import static com.getpcpanel.mqtt.MqttTopicHelper.ActionType.button;
import static com.getpcpanel.mqtt.MqttTopicHelper.ColorType.knob;
import static com.getpcpanel.mqtt.MqttTopicHelper.ColorType.label;
import static com.getpcpanel.mqtt.MqttTopicHelper.ColorType.logo;
import static com.getpcpanel.mqtt.MqttTopicHelper.ColorType.slider;
import static com.getpcpanel.mqtt.MqttTopicHelper.ValueType.analog;
import static com.getpcpanel.mqtt.MqttTopicHelper.ValueType.brightness;

import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.util.TriConsumer;
import org.springframework.context.event.EventListener;
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
import com.getpcpanel.profile.SingleSliderLabelLightingConfig;
import com.getpcpanel.profile.SingleSliderLightingConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.EntryStream;

@Log4j2
@Service
@RequiredArgsConstructor
public class MqttDeviceService {
    private final MqttService mqtt;
    private final SaveService saveService;
    private final DeviceHolder deviceHolder;
    private final MqttHomeAssistantHelper mqttHomeAssistantHelper;
    private final MqttTopicHelper mqttTopicHelper;

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

    @EventListener
    public void deviceConnected(DeviceFullyConnectedEvent event) {
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

        if (saveService.get().getMqtt().homeAssistantDiscovery()) {
            mqttHomeAssistantHelper.discover(saveService.get().getMqtt(), device);
        }
    }

    private void buildSubscriptions(Device device, LightingConfig lighting) {
        var topicHelper = mqttTopicHelper.device(device.getSerialNumber());
        Runnable andThen = () -> device.setLighting(lighting, true);

        subscribeTo(topicHelper.valueTopic(brightness, 0), payload -> lighting.setGlobalBrightness(NumberUtils.toInt(payload, 100)), andThen);
        subscribeTo(lighting.getKnobConfigs(), topicHelper, knob, (idx, payload, knob) -> knob.setColor1(payload), andThen);
        subscribeTo(lighting.getSliderConfigs(), topicHelper, slider, (idx, payload, knob) -> knob.setColor1(payload), andThen);
        subscribeTo(lighting.getSliderLabelConfigs(), topicHelper, label, (idx, payload, knob) -> knob.setColor(payload), andThen);
        if (lighting.getLogoConfig() != null) {
            subscribeTo(topicHelper.lightTopic(logo, 0), payload -> lighting.getLogoConfig().setColor(payload), andThen);
        }
    }

    private <T> void subscribeTo(T[] items, DeviceMqttTopicHelper topicHelper, ColorType type, TriConsumer<Integer, String, T> consumer, Runnable andThen) {
        EntryStream.of(items).forKeyValue((idx, knob) -> {
            var topic = topicHelper.lightTopic(type, idx);
            subscribeTo(topic, payload -> consumer.accept(idx, payload, knob), andThen);
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
        sendColors(lighting.getKnobConfigs(), mqttHelper, knob, SingleKnobLightingConfig::getColor1);
        sendColors(lighting.getSliderConfigs(), mqttHelper, slider, SingleSliderLightingConfig::getColor1);
        sendColors(lighting.getSliderLabelConfigs(), mqttHelper, label, SingleSliderLabelLightingConfig::getColor);
        if (lighting.getLogoConfig() != null) {
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
}
