package com.getpcpanel.integration.mqtt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.profile.SaveService.SaveEvent;
import com.getpcpanel.profile.dto.MqttSettings;
import com.getpcpanel.util.Debouncer;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * MQTT integration backed by the Eclipse Paho mqttv5 client.
 *
 * <p>Paho is a pure-Java client (plain sockets, no Netty, no RxJava), chosen to keep the native
 * image's footprint down. Because Paho throws when you subscribe before the asynchronous connect
 * has completed (unlike HiveMQ, which queued operations), subscriptions are remembered and
 * (re)applied from {@link MqttCallback#connectComplete} — which also makes them survive Paho's
 * automatic reconnects.
 */
@Log4j2
@ApplicationScoped
public class MqttService {
    private static final int QOS = 0;
    static final int ORDER_OF_SAVE = 0;
    public static final String IGNORE_CORRELATION = "pcpanel";
    @Inject
    Event<Object> eventBus;
    @Inject
    ObjectMapper objectMapper;
    @Inject
    Debouncer debouncer;
    @Inject
    MqttTopicHelper topicHelper;
    private MqttSettings connectedSettings;
    @Nullable private MqttAsyncClient mqttClient;
    private String availabilityTopic = "";
    // Topic filter -> listener, remembered so subscriptions can be re-applied on (re)connect.
    private final Map<String, IMqttMessageListener> subscriptions = new ConcurrentHashMap<>();

    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
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
            if (!isConnected()) {
                log.trace("Not connected, not sending to {}: {}", topic, new String(payload));
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug("Sending to {}: {}", topic, new String(payload));
            }
            publish(topic, payload, true, triggerSelf);
        };

        if (immediate) {
            send.run();
            return;
        }
        debouncer.rateLimit(new TopicKey(topic), send, 250, TimeUnit.MILLISECONDS);
    }

    private void publish(String topic, @Nullable byte[] payload, boolean retain, boolean triggerSelf) {
        var client = mqttClient;
        if (client == null) {
            return;
        }
        var message = new MqttMessage(payload == null ? new byte[0] : payload);
        message.setQos(QOS);
        message.setRetained(retain);
        if (!triggerSelf) {
            // Mark our own publishes so the subscription callback can ignore them.
            var props = new MqttProperties();
            props.setCorrelationData(IGNORE_CORRELATION.getBytes());
            message.setProperties(props);
        }
        try {
            client.publish(topic, message);
        } catch (MqttException e) {
            log.error("Failed to publish to {}", topic, e);
        }
    }

    public void remove(String topic) {
        if (mqttClient == null) {
            log.warn("Removing {} but mqttClient is null", topic);
            return;
        }
        log.debug("Clear topic: {}", topic);
        publish(topic, null, true, true);
    }

    public void removeAll(String topic) {
        var client = mqttClient;
        if (client == null) {
            log.warn("Removing all {} but mqttClient is null", topic);
            return;
        }
        log.debug("Clear all topics: {}", topic);
        var toRemove = new ArrayList<String>();
        try {
            // Retained messages are delivered right after subscribing; collect them briefly.
            // Array-listener overload avoids the Paho 1.2.5 single-listener IndexOutOfBoundsException bug.
            client.subscribe(new MqttSubscription[] { new MqttSubscription(topic, QOS) }, null, null,
                    new IMqttMessageListener[] { (t, msg) -> toRemove.add(t) }, new MqttProperties())
                    .waitForCompletion(2000);
            Thread.sleep(300);
            client.unsubscribe(topic).waitForCompletion(2000);
        } catch (MqttException e) {
            log.error("Failed to enumerate retained topics for {}", topic, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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

    @Priority(ORDER_OF_SAVE)
    public void saveChanged(@Observes SaveEvent event) {
        var mqttSettings = event.save().getMqtt();
        if (mqttSettings == null || !mqttSettings.enabled()) {
            disconnect();
            eventBus.fire(new MqttStatusEvent(false));
            connectedSettings = MqttSettings.DEFAULT;
            return;
        }
        if (mqttSettings.equals(connectedSettings)) {
            return;
        }

        log.trace("Save changed, starting mqtt");
        connect(mqttSettings);
        connectedSettings = mqttSettings;
        eventBus.fire(new MqttStatusEvent(true));
    }

    private void connect(MqttSettings mqttSettings) {
        disconnect();
        availabilityTopic = topicHelper.availabilityTopic(mqttSettings);
        var scheme = mqttSettings.secure() ? "ssl" : "tcp";
        var serverUri = scheme + "://" + mqttSettings.host() + ":" + mqttSettings.port();
        try {
            var client = new MqttAsyncClient(serverUri, UUID.randomUUID().toString(), new MemoryPersistence());
            client.setCallback(new Callback());

            var options = new MqttConnectionOptions();
            options.setUserName(mqttSettings.username());
            options.setPassword(mqttSettings.password().getBytes());
            options.setCleanStart(true);
            options.setAutomaticReconnect(true);
            var will = new MqttMessage(new byte[0]);
            will.setQos(QOS);
            will.setRetained(true);
            options.setWill(availabilityTopic, will);

            mqttClient = client;
            client.connect(options);
        } catch (Exception e) {
            // Never let an MQTT misconfiguration (bad host/scheme/credentials) break app startup.
            log.error("Failed to connect to MQTT server {}", serverUri, e);
        }
    }

    private void disconnect() {
        var client = mqttClient;
        mqttClient = null;
        subscriptions.clear();
        if (client == null) {
            return;
        }
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        } catch (MqttException e) {
            log.debug("Error while disconnecting MQTT client", e);
        }
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
        IMqttMessageListener listener = (t, publish) -> {
            var props = publish.getProperties();
            var cd = props == null ? null : props.getCorrelationData();
            var ignore = cd != null && IGNORE_CORRELATION.equals(new String(cd, StandardCharsets.UTF_8));
            if (!ignore) {
                consumer.accept(converter.apply(publish.getPayload()));
                send(topic, publish.getPayload(), true, false); // Ensure that the message isn't picked up after restart
            }
        };
        subscriptions.put(topic, listener);
        if (isConnected()) {
            doSubscribe(topic, listener);
        }
    }

    private void doSubscribe(String topic, IMqttMessageListener listener) {
        var client = mqttClient;
        if (client == null) {
            return;
        }
        try {
            // Use the array-listener overload: the single-listener overload in Paho 1.2.5 crashes
            // with IndexOutOfBoundsException because it reads getSubscriptionIdentifiers().get(0) on an empty list.
            client.subscribe(new MqttSubscription[] { new MqttSubscription(topic, QOS) }, null, null,
                    new IMqttMessageListener[] { listener }, new MqttProperties());
        } catch (MqttException e) {
            log.error("Failed to subscribe to {}", topic, e);
        }
    }

    private final class Callback implements MqttCallback {
        @Override
        public void connectComplete(boolean reconnect, String serverURI) {
            log.info("Connected to MQTT server{}", reconnect ? " (reconnect)" : "");
            send(availabilityTopic, "online", true);
            subscriptions.forEach(MqttService.this::doSubscribe);
        }

        @Override
        public void disconnected(MqttDisconnectResponse disconnectResponse) {
            log.debug("MQTT disconnected: {}", disconnectResponse.getReasonString());
        }

        @Override
        public void mqttErrorOccurred(MqttException exception) {
            log.debug("MQTT error", exception);
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            // Per-subscription listeners handle delivery.
        }

        @Override
        public void deliveryComplete(IMqttToken token) {
            // no-op
        }

        @Override
        public void authPacketArrived(int reasonCode, MqttProperties properties) {
            // no-op
        }
    }

    private record TopicKey(String topic) {
    }
}
