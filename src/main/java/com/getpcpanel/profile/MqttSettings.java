package com.getpcpanel.profile;

public record MqttSettings(boolean enabled, String host, Integer port, String username, String password, boolean secure,
                           String baseTopic,
                           boolean homeAssistantDiscovery, String homeAssistantBaseTopic) {
    public static final MqttSettings DEFAULT = new MqttSettings(false, "", 1883, "", "", false, "buttonplus", true, "homeassistant");

    public MqttSettings {
        if (port == null) {
            port = DEFAULT.port;
        }
    }
}
