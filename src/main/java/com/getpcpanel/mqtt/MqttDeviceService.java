package com.getpcpanel.mqtt;

import static com.getpcpanel.mqtt.MqttService.ORDER_OF_SAVE;
import static com.getpcpanel.mqtt.MqttTopicHelper.ActionType.button;
import static com.getpcpanel.mqtt.MqttTopicHelper.ColorType.dial;
import static com.getpcpanel.mqtt.MqttTopicHelper.ColorType.label;
import static com.getpcpanel.mqtt.MqttTopicHelper.ColorType.logo;
import static com.getpcpanel.mqtt.MqttTopicHelper.ColorType.slider;
import static com.getpcpanel.mqtt.MqttTopicHelper.ValueType.analog;
import static com.getpcpanel.mqtt.MqttTopicHelper.ValueType.brightness;

import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import com.getpcpanel.device.Device;
import com.getpcpanel.hid.ButtonClickEvent;
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
    private final MqttDeviceColorService deviceColorService;

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
        mqtt.send(mqttTopicHelper.availabilityTopic(), "online", true); // Just to be sure, we might have cleared all the topics
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
            var topic = mqttTopicHelper.buttonUpDownTopic(btn.serialNum(), button, btn.button());
            mqtt.send(topic, btn.pressed() ? "down" : "up", true);
        });
    }

    @EventListener
    public void buttonPress(ButtonClickEvent btn) {
        saveService.getProfile(btn.serialNum()).ifPresent(profile -> {
            var topic = mqttTopicHelper.eventTopic(btn.serialNum(), button, btn.button());
            mqtt.send(topic, new MqttEvent(btn.dblClick() ? "double_click" : "click"), true);
        });
    }

    private void initialize(Device device) {
        var lighting = device.getLightingConfig();
        if (lighting.getLightingMode() != LightingConfig.LightingMode.CUSTOM) {
            log.debug("Only custom lighting will be written to mqtt");
            return;
        }

        writeLighting(device, lighting);
        writeButtons(device);
        deviceColorService.buildSubscriptions(device, lighting);

        if (saveService.get().getMqtt().homeAssistant().enableDiscovery()) {
            mqttHomeAssistantHelper.discover(saveService.get().getMqtt(), device);
        }
    }

    private void clear(Device device) {
        var baseTopic = mqttTopicHelper.baseTopicFilter();
        mqtt.removeAll(baseTopic);
        mqttHomeAssistantHelper.clearAll(saveService.get().getMqtt());
    }

    private void writeLighting(Device device, LightingConfig lighting) {
        var mqttHelper = mqttTopicHelper.device(device.getSerialNumber());

        mqtt.send(mqttHelper.valueTopic(brightness, 0), String.valueOf(lighting.getGlobalBrightness()), false);
        sendColors(lighting.getKnobConfigs(), mqttHelper, dial, SingleKnobLightingConfig::getColor1);
        sendColors(lighting.getSliderConfigs(), mqttHelper, slider, SingleSliderLightingConfig::getColor1);
        sendColors(lighting.getSliderLabelConfigs(), mqttHelper, label, SingleSliderLabelLightingConfig::getColor);
        if (device.getDeviceType().isHasLogoLed() && lighting.getLogoConfig() != null) {
            deviceColorService.sendColor(mqttHelper.lightTopic(logo, 0), toColorString(lighting.getLogoConfig().getColor()), false);
        }
    }

    private <T> void sendColors(T[] items, DeviceMqttTopicHelper mqttHelper, ColorType colorType, Function<T, String> colorMapper) {
        EntryStream.of(items).forKeyValue((idx, knob) -> {
            var lightTopic = mqttHelper.lightTopic(colorType, idx);
            deviceColorService.sendColor(lightTopic, toColorString(colorMapper.apply(knob)), true);
        });
    }

    private @Nonnull String toColorString(@Nullable String color) {
        return color == null ? "#000000" : color;
    }

    private void writeButtons(Device device) {
        for (var i = 0; i < device.getDeviceType().getButtonCount(); i++) {
            mqtt.send(mqttTopicHelper.buttonUpDownTopic(device.getSerialNumber(), button, i), "up", true);
        }
    }

    record MqttEvent(String event_type) {
    }
}
