package com.getpcpanel.mqtt;

import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;

import com.getpcpanel.profile.MqttSettings;
import com.getpcpanel.profile.SaveService;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@ApplicationScoped
class MqttTopicHelper {
    @Inject
    SaveService saveService;

    public DeviceMqttTopicHelper device(String deviceSerial) {
        return new DeviceMqttTopicHelper(deviceSerial);
    }

    public String availabilityTopic() {
        return baseJoining("available");
    }

    public String availabilityTopic(MqttSettings settings) {
        return baseJoining(settings, "available");
    }

    public String baseTopicFilter() {
        return baseJoining("#");
    }

    public String valueTopic(String deviceSerial, ValueType type, int index) {
        return baseJoining(deviceSerial, "values", type.name() + index);
    }

    public String buttonUpDownTopic(String deviceSerial, ActionType type, int index) {
        return baseJoining(deviceSerial, "actions", type.name() + index);
    }

    public String eventTopic(String deviceSerial, ActionType type, int index) {
        return baseJoining(deviceSerial, "events", type.name() + index);
    }

    public String lightTopic(String deviceSerial, ColorType type, int index) {
        return baseJoining(deviceSerial, "lighting", type.name(), index);
    }

    private String baseJoining(Object... parts) {
        return StreamEx.of(parts).prepend(getSettings().baseTopic()).joining("/");
    }

    private String baseJoining(MqttSettings settings, Object... parts) {
        return StreamEx.of(parts).prepend(settings.baseTopic()).joining("/");
    }

    private MqttSettings getSettings() {
        return saveService.get().getMqtt();
    }

    enum ValueType {
        analog,
        brightness,
    }

    enum ActionType {
        button,
    }

    enum ColorType {
        dial,
        slider,
        label,
        logo,
    }

        class DeviceMqttTopicHelper {
        private final String deviceSerial;

        DeviceMqttTopicHelper(String deviceSerial) {
            this.deviceSerial = deviceSerial;
        }

        public String valueTopic(ValueType type, int index) {
            return MqttTopicHelper.this.valueTopic(deviceSerial, type, index);
        }

        public String lightTopic(ColorType type, int index) {
            return MqttTopicHelper.this.lightTopic(deviceSerial, type, index);
        }
    }
}
