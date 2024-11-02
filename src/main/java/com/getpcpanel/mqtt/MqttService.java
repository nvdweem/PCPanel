package com.getpcpanel.mqtt;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.profile.MqttSettings;
import com.getpcpanel.profile.SaveService;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
public class MqttService {
    private final SaveService saveService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private MqttSettings connectedSettings;
    @Nullable private Mqtt5Client mqttClient;

    public boolean isConnected() {
        return mqttClient != null && mqttClient.getState().isConnected();
    }

    public void send(String topic, Object payload) {
        if (Objects.requireNonNull(payload) instanceof String s) {
            send(topic, s.getBytes());
        } else {
            try {
                send(topic, objectMapper.writeValueAsBytes(payload));
            } catch (Exception e) {
                log.error("Failed to serialize payload", e);
            }
        }
    }

    public void send(String topic, byte[] payload) {
        if (log.isDebugEnabled()) {
            log.debug("Sending to {}: {}", topic, new String(payload));
        }
        mqttClient.toAsync().publishWith()
                  .topic(topic)
                  .payload(payload)
                  .retain(true)
                  .send();
    }

    @PostConstruct
    @EventListener(SaveService.SaveEvent.class)
    public void saveChanged() {
        var mqttSettings = saveService.get().getMqtt();
        if (mqttSettings == null || !mqttSettings.enabled()) {
            disconnect();
            eventPublisher.publishEvent(new MqttStatusEvent(false));
            return;
        }
        if (mqttSettings.equals(connectedSettings)) {
            return;
        }

        log.trace("Save changed, starting mqtt");
        connect(mqttSettings);
        connectedSettings = mqttSettings;
        eventPublisher.publishEvent(new MqttStatusEvent(true));
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

    private void disconnect() {
        if (mqttClient == null) {
            return;
        }
        mqttClient.toBlocking().disconnect();
        mqttClient = null;
    }

    public <T> void subscribe(String topic, Class<T> clazz, Consumer<T> consumer) {
        subscribe(topic, bytes -> {
            try {
                return objectMapper.readValue(bytes, clazz);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, consumer);
    }

    public void subscribeString(String topic, Consumer<String> consumer) {
        subscribe(topic, String::new, consumer);
    }

    public <T> void subscribe(String topic, Function<byte[], T> converter, Consumer<T> consumer) {
        mqttClient.toAsync().subscribeWith()
                  .topicFilter(topic)
                  .callback(publish -> consumer.accept(converter.apply(publish.getPayloadAsBytes())))
                  .send();
    }
}
