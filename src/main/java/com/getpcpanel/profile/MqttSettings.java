package com.getpcpanel.profile;

public record MqttSettings(boolean enabled, String host, Integer port, String username, String password, boolean secure,
                           String baseTopic,
                           HomeAssistantSettings homeAssistant) {
    public static final int DEFAULT_MQTT_PORT = 1883;
    public static final MqttSettings DEFAULT = new MqttSettings(false, "", DEFAULT_MQTT_PORT, "", "", false, "buttonplus", HomeAssistantSettings.DEFAULT);

    public MqttSettings {
        if (port == null) {
            port = DEFAULT.port;
        }
        if (homeAssistant == null) {
            homeAssistant = HomeAssistantSettings.DEFAULT;
        }
    }

    public record HomeAssistantSettings(boolean enableDiscovery, String baseTopic, boolean availability) {
        public static final HomeAssistantSettings DEFAULT = new HomeAssistantSettings(false, "homeassistant", true);
    }
}
