package com.getpcpanel.mqtt.osc;

import java.util.UUID;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.getpcpanel.device.Device;
import com.getpcpanel.hid.DeviceCommunicationHandler;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.hid.DeviceHolder.DeviceFullyConnectedEvent;
import com.getpcpanel.profile.LightingConfig;
import com.getpcpanel.profile.MqttSettings;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.SingleKnobLightingConfig;
import com.getpcpanel.profile.SingleSliderLabelLightingConfig;
import com.getpcpanel.profile.SingleSliderLightingConfig;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.EntryStream;

@Log4j2
@Service
@RequiredArgsConstructor
public class MqttService {
    private final SaveService saveService;
    private final DeviceHolder deviceHolder;
    private MqttSettings connectedSettings;
    @Nullable private Mqtt5Client mqttClient;

    @PostConstruct
    @EventListener(SaveService.SaveEvent.class)
    public void saveChanged() {
        var mqttSettings = saveService.get().getMqtt();
        if (mqttSettings == null || !mqttSettings.enabled()) {
            disconnect();
            return;
        }
        if (mqttSettings.equals(connectedSettings)) {
            return;
        }

        log.trace("Save changed, starting mqtt");
        connect(mqttSettings);
        sendInitial();
        setup();
        connectedSettings = mqttSettings;
    }

    @EventListener
    public void deviceConnected(DeviceFullyConnectedEvent event) {
        sendInitial(event.device());
    }

    private void sendInitial() {
        deviceHolder.all().forEach(this::sendInitial);
    }

    private void sendInitial(Device device) {
        var lighting = device.getLightingConfig();
        if (lighting.getLightingMode() != LightingConfig.LightingMode.CUSTOM) {
            log.debug("Only custom lighting will be written to mqtt");
            return;
        }

        writeLighting(device, lighting);
    }

    private void writeLighting(Device device, LightingConfig lighting) {
        var mqttSettings = saveService.get().getMqtt();
        var topic = StringUtils.joinWith("/", mqttSettings.baseTopic(), device.getSerialNumber(), "lighting");

        sendColors(lighting.getKnobConfigs(), topic, "knob", SingleKnobLightingConfig::getColor1, lighting.getGlobalBrightness());
        sendColors(lighting.getSliderConfigs(), topic, "slider", SingleSliderLightingConfig::getColor1, lighting.getGlobalBrightness());
        sendColors(lighting.getSliderLabelConfigs(), topic, "label", SingleSliderLabelLightingConfig::getColor, lighting.getGlobalBrightness());
        sendMqtt(StringUtils.joinWith("/", topic, "logo"), toColorString(lighting.getLogoConfig().getColor(), lighting.getGlobalBrightness()));
    }

    private <T> void sendColors(T[] items, String baseTopic, String topic, Function<T, String> colorMapper, int brightness) {
        EntryStream.of(items).forKeyValue((idx, knob) -> {
            var sliderTopic = StringUtils.joinWith("/", baseTopic, topic + idx);
            sendMqtt(sliderTopic, toColorString(colorMapper.apply(knob), brightness));
        });
    }

    private byte[] toColorString(String color, int globalBrightness) {
        return (color == null ? "000000" : color).getBytes();
    }

    private void connect(MqttSettings mqttSettings) {
        mqttClient = MqttClient.builder()
                               .identifier(UUID.randomUUID().toString())
                               .serverHost(mqttSettings.host())
                               .serverPort(mqttSettings.port())
                               .useMqttVersion5()
                               .simpleAuth().username(mqttSettings.username()).password(mqttSettings.password().getBytes()).applySimpleAuth()
                               .build();
        mqttClient.toBlocking().connect();
        log.info("Connected to MQTT server");
    }

    private void setup() {
    }

    private void disconnect() {
        if (mqttClient == null) {
            return;
        }
        mqttClient.toBlocking().disconnect();
        mqttClient = null;
    }

    @EventListener
    public void dialAction(DeviceCommunicationHandler.KnobRotateEvent dial) {
        if (!isConnected()) {
            return;
        }

        var mqttSettings = saveService.get().getMqtt();
        saveService.getProfile(dial.serialNum()).ifPresent(profile -> {
            var topic = StringUtils.joinWith("/", mqttSettings.baseTopic(), dial.serialNum(), "values", "knob" + dial.knob());
            sendMqtt(topic, String.valueOf(dial.value()).getBytes());
        });
    }

    private boolean isConnected() {
        return mqttClient != null && mqttClient.getState().isConnected();
    }

    @EventListener
    public void dialAction(DeviceCommunicationHandler.ButtonPressEvent button) {
        var mqttSettings = saveService.get().getMqtt();
        saveService.getProfile(button.serialNum()).ifPresent(profile -> {
            var topic = StringUtils.joinWith("/", mqttSettings.baseTopic(), button.serialNum(), "actions", "button" + button.button());
            sendMqtt(topic, (button.pressed() ? "click" : "release").getBytes());
        });
    }

    private void sendMqtt(String topic, byte[] payload) {
        mqttClient.toAsync().publishWith()
                  .topic(topic)
                  .payload(payload)
                  .send();
    }
}
