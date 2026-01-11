package com.getpcpanel.mqtt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.profile.MqttSettings;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.util.Debouncer;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
public class MqttService {
    static final int ORDER_OF_SAVE = 0;
    public static final String IGNORE_CORRELATION = "pcpanel";
    private final SaveService saveService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final Debouncer debouncer;
    private final MqttTopicHelper topicHelper;
    private MqttSettings connectedSettings;
    @Nullable private Mqtt5Client mqttClient;

    public boolean isConnected() {
        return mqttClient != null && mqttClient.getState().isConnected();
    }

    public void send(String topic, Object payload, boolean immediate) {
        send(topic, payload, immediate, false);
    }

    public void send(String topic, Object payload, boolean immediate, boolean triggerSelf) {
        if (Objects.requireNonNull(payload) instanceof String s) {
            send(topic, s.getBytes(), immediate, triggerSelf);
        } else {
            try {
                send(topic, objectMapper.writeValueAsBytes(payload), immediate, triggerSelf);
            } catch (Exception e) {
                log.error("Failed to serialize payload", e);
            }
        }
    }

    public void send(String topic, byte[] payload, boolean immediate, boolean triggerSelf) {
        Runnable send = () -> {
            if (mqttClient == null || !isConnected()) {
                log.trace("Not connected, not sending to {}: {}", topic, new String(payload));
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug("Sending to {}: {}", topic, new String(payload));
            }
            mqttClient.toAsync().publishWith()
                      .topic(topic)
                      .payload(payload)
                      .retain(true)
                      .correlationData(triggerSelf ? null : IGNORE_CORRELATION.getBytes()) // Will be ignored in the subscription
                      .send();
        };

        if (immediate) {
            send.run();
            return;
        }
        debouncer.rateLimit(new TopicKey(topic), send, 250, TimeUnit.MILLISECONDS);
    }

    public void remove(String topic) {
        if (mqttClient == null) {
            log.warn("Removing {} but mqttClient is null", topic);
            return;
        }
        log.debug("Clear topic: {}", topic);
        mqttClient.toAsync().publishWith()
                  .topic(topic)
                  .payload((byte[]) null)
                  .retain(true)
                  .send();
    }

    public void removeAll(String topic) {
        if (mqttClient == null) {
            log.warn("Removing all {} but mqttClient is null", topic);
            return;
        }
        log.debug("Clear all topics: {}", topic);
        var client = mqttClient.toBlocking();
        var toRemove = new ArrayList<String>();

        try (var publishes = client.publishes(MqttGlobalPublishFilter.SUBSCRIBED)) {
            client.subscribeWith().topicFilter(topic).send();
            Optional<Mqtt5Publish> entry;

            do {
                try {
                    entry = publishes.receive(100, TimeUnit.MILLISECONDS);
                    entry.ifPresent(mqtt5Publish -> toRemove.add(mqtt5Publish.getTopic().toString()));
                } catch (InterruptedException e) {
                    log.error("Failed to receive publish", e);
                    break;
                }
            } while (entry.isPresent());

            client.unsubscribeWith().topicFilter(topic).send();
        }

        var topicRegex = topicToRegex(topic);
        toRemove.stream().filter(t -> topicRegex.matcher(t).matches()).forEach(this::remove);
    }

    private Pattern topicToRegex(String topic) {
        return Pattern.compile(
                topic.replace("/", "\\/")
                     .replace("#", ".*")
                     .replace("+", "[^/]+")
        );
    }

    @Order(ORDER_OF_SAVE)
    @PostConstruct
    @EventListener(SaveService.SaveEvent.class)
    public void saveChanged() {
        var mqttSettings = saveService.get().getMqtt();
        if (mqttSettings == null || !mqttSettings.enabled()) {
            disconnect();
            eventPublisher.publishEvent(new MqttStatusEvent(false));
            connectedSettings = MqttSettings.DEFAULT;
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
        var availabilityTopic = topicHelper.availabilityTopic();
        var builder = MqttClient.builder()
                                .identifier(UUID.randomUUID().toString())
                                .serverHost(mqttSettings.host())
                                .serverPort(mqttSettings.port())
                                .useMqttVersion5()
                                .automaticReconnectWithDefaultConfig()
                                .willPublish().topic(availabilityTopic).payload((byte[]) null).retain(true).applyWillPublish()
                                .simpleAuth().username(mqttSettings.username()).password(mqttSettings.password().getBytes()).applySimpleAuth();
        if (mqttSettings.secure()) {
            builder = builder.sslWithDefaultConfig();
        }
        mqttClient = builder.build();
        mqttClient.toAsync().connect()
                  .thenAccept(connAck -> {
                      send(availabilityTopic, "online", true);
                      log.info("Connected to MQTT server");
                  });
    }

    private void disconnect() {
        if (mqttClient == null) {
            return;
        }
        mqttClient.toAsync().disconnect();
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
        if (mqttClient == null) {
            log.warn("Subscribing to {} but mqttClient is null", topic);
            return;
        }
        mqttClient.toAsync().subscribeWith()
                  .topicFilter(topic)
                  .callback(publish -> {
                      var ignore = publish.getCorrelationData().map(cd -> StandardCharsets.UTF_8.decode(cd).toString().equals(IGNORE_CORRELATION)).orElse(false);
                      if (!ignore) {
                          consumer.accept(converter.apply(publish.getPayloadAsBytes()));
                          send(topic, publish.getPayloadAsBytes(), true, false); // Ensure that the message isn't picked up after restart
                      }
                  })
                  .send();
    }

    private record TopicKey(String topic) {
    }
}
