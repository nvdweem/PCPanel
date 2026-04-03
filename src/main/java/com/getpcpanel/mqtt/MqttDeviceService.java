package com.getpcpanel.mqtt;

import static com.getpcpanel.mqtt.MqttService.ORDER_OF_SAVE;
import static com.getpcpanel.mqtt.MqttTopicHelper.ActionType.button;
import static com.getpcpanel.mqtt.MqttTopicHelper.ColorType.dial;
import static com.getpcpanel.mqtt.MqttTopicHelper.ColorType.label;
import static com.getpcpanel.mqtt.MqttTopicHelper.ColorType.logo;
import static com.getpcpanel.mqtt.MqttTopicHelper.ColorType.slider;
import static com.getpcpanel.mqtt.MqttTopicHelper.ValueType.analog;
import static com.getpcpanel.mqtt.MqttTopicHelper.ValueType.brightness;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import jakarta.inject.Inject;
import jakarta.enterprise.event.Observes;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

import com.getpcpanel.device.Device;
import com.getpcpanel.device.GlobalBrightnessChangedEvent;
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
@ApplicationScoped
public class MqttDeviceService {
    @Inject
    MqttService mqtt;
    @Inject
    SaveService saveService;
    @Inject
    DeviceHolder deviceHolder;
    @Inject
    MqttHomeAssistantHelper mqttHomeAssistantHelper;
    @Inject
    MqttTopicHelper mqttTopicHelper;
    @Inject
    MqttDeviceColorService deviceColorService;
    private final Set<Device> initializedDevices = new HashSet<>();

    @Priority(ORDER_OF_SAVE + 1) // Ensure we are disconnected if the setting is turned off
        public void saveChanged() {
        if (mqtt.isConnected()) {
            initialize();
        }
    }

        public void mqttConnected(@Observes MqttStatusEvent event) {
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

        public void deviceConnected(@Observes DeviceFullyConnectedEvent event) {
        if (!mqtt.isConnected()) {
            return;
        }
        initialize(event.device());
    }

        public void dialAction(@Observes DeviceCommunicationHandler.KnobRotateEvent dial) {
        if (!mqtt.isConnected()) {
            return;
        }

        saveService.getProfile(dial.serialNum()).ifPresent(profile -> {
            var topic = mqttTopicHelper.valueTopic(dial.serialNum(), analog, dial.knob());
            mqtt.send(topic, String.valueOf(dial.value()), false);
        });
    }

        public void buttonPress(@Observes DeviceCommunicationHandler.ButtonPressEvent btn) {
        if (!mqtt.isConnected()) {
            return;
        }
        saveService.getProfile(btn.serialNum()).ifPresent(profile -> {
            var topic = mqttTopicHelper.buttonUpDownTopic(btn.serialNum(), button, btn.button());
            mqtt.send(topic, btn.pressed() ? "down" : "up", true);
        });
    }

        public void buttonPress(@Observes ButtonClickEvent btn) {
        if (!mqtt.isConnected()) {
            return;
        }
        saveService.getProfile(btn.serialNum()).ifPresent(profile -> {
            var topic = mqttTopicHelper.eventTopic(btn.serialNum(), button, btn.button());
            mqtt.send(topic, new MqttEvent(btn.dblClick() ? "double_click" : "click"), true);
        });
    }

        public void globalBrightnessChange(@Observes GlobalBrightnessChangedEvent event) {
        if (!mqtt.isConnected()) {
            return;
        }
        mqtt.send(mqttTopicHelper.valueTopic(event.serialNum(), brightness, 0), String.valueOf(event.getBrightness()), false);
    }

    private void initialize(Device device) {
        if (initializedDevices.contains(device)) {
            return;
        }
        initializedDevices.add(device);

        var lighting = device.lightingConfig();
        if (lighting.lightingMode() != LightingConfig.LightingMode.CUSTOM) {
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
        initializedDevices.remove(device);
        var baseTopic = mqttTopicHelper.baseTopicFilter();
        mqtt.removeAll(baseTopic);
        mqttHomeAssistantHelper.clearAll(saveService.get().getMqtt());
    }

    private void writeLighting(Device device, LightingConfig lighting) {
        var mqttHelper = mqttTopicHelper.device(device.getSerialNumber());

        mqtt.send(mqttHelper.valueTopic(brightness, 0), String.valueOf(lighting.getGlobalBrightness()), false);
        sendColors(lighting.knobConfigs(), mqttHelper, dial, SingleKnobLightingConfig::getColor1);
        sendColors(lighting.sliderConfigs(), mqttHelper, slider, SingleSliderLightingConfig::getColor1);
        sendColors(lighting.sliderLabelConfigs(), mqttHelper, label, SingleSliderLabelLightingConfig::getColor);
        if (device.deviceType().isHasLogoLed() && lighting.logoConfig() != null) {
            deviceColorService.sendColor(mqttHelper.lightTopic(logo, 0), toColorString(lighting.logoConfig().getColor()), false);
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
        for (var i = 0; i < device.deviceType().getButtonCount(); i++) {
            mqtt.send(mqttTopicHelper.buttonUpDownTopic(device.getSerialNumber(), button, i), "up", true);
        }
    }

    record MqttEvent(String event_type) {
    }
}
