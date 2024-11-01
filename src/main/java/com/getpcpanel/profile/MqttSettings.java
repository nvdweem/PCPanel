package com.getpcpanel.profile;

public record MqttSettings(boolean enabled, String host, Integer port, String username, String password, boolean secure, String baseTopic, boolean homeAssistantDiscovery) {
    public MqttSettings {
        if (port == null) {
            port = 1883;
        }
    }
}
