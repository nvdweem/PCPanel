package com.getpcpanel.mqtt;

import java.util.List;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.getpcpanel.device.Device;
import com.getpcpanel.profile.MqttSettings;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
public class MqttHomeAssistantHelper {
    private final MqttTopicHelper topicHelper;
    private final MqttService mqttService;
    @Value("${application.version}") private String version;

    public void discover(MqttSettings settings, Device device) {
        var haDevice = new HomeAssistantDevice(
                version,
                List.of(device.getSerialNumber()),
                "PCPanel Holdings, LLC",
                device.getDeviceType().getNiceName(),
                device.getSerialNumber(),
                device.getSerialNumber()
        );

        addAnalogValueConfigs(settings, device, haDevice);
        addBrightnessDevice(settings, device, haDevice);
        addLights(settings, device, haDevice);
        addButtons(settings, device, haDevice);
    }

    private void addLights(MqttSettings settings, Device device, HomeAssistantDevice haDevice) {
        addLogoLight(settings, device, haDevice);
        addControlLights(settings, device, haDevice);
    }

    private void addControlLights(MqttSettings settings, Device device, HomeAssistantDevice haDevice) {
        for (var i = 0; i < device.getDeviceType().getAnalogCount(); i++) {
            var buttonCount = device.getDeviceType().getButtonCount();
            var type = i < buttonCount ? MqttTopicHelper.ColorType.knob : MqttTopicHelper.ColorType.slider;
            var idx = i < buttonCount ? i : i - buttonCount;

            addControlLightConfig(settings, device, haDevice, i, type, idx);
            if (type == MqttTopicHelper.ColorType.slider) {
                addSliderLabelLightConfig(settings, device, haDevice, i, idx, type);
            }
        }
    }

    private void addControlLightConfig(MqttSettings settings, Device device, HomeAssistantDevice haDevice, int i, MqttTopicHelper.ColorType type, int idx) {
        var controlConfigTopic = configTopicFor(settings, device, "light", "control", i);
        var controlValueTopic = topicHelper.lightTopic(device.getSerialNumber(), type, idx);

        var config = new HomeAssistantLightConfig(
                haDevice,
                StringUtils.capitalize(type.name()) + " " + (idx + 1) + " Light",
                device.getSerialNumber() + "_" + type.name() + "_" + idx,
                controlValueTopic,
                "mdi:lightbulb"
        );
        mqttService.send(controlConfigTopic, config);
    }

    private void addSliderLabelLightConfig(MqttSettings settings, Device device, HomeAssistantDevice haDevice, int i, int idx, MqttTopicHelper.ColorType type) {
        var labelConfigTopic = lightTopicFor(settings, device, "label_" + idx);
        var labelValueTopic = topicHelper.lightTopic(device.getSerialNumber(), MqttTopicHelper.ColorType.label, idx);

        var labelConfig = new HomeAssistantLightConfig(
                haDevice,
                StringUtils.capitalize(type.name()) + " " + (idx + 1) + " Label Light",
                device.getSerialNumber() + "_label_" + idx,
                labelValueTopic,
                "mdi:lightbulb"
        );
        mqttService.send(labelConfigTopic, labelConfig);
    }

    private void addAnalogValueConfigs(MqttSettings settings, Device device, HomeAssistantDevice haDevice) {
        for (var i = 0; i < device.getDeviceType().getAnalogCount(); i++) {
            var configTopic = configTopicFor(settings, device, "number", "analog", i);
            var valueTopic = topicHelper.valueTopic(device.getSerialNumber(), MqttTopicHelper.ValueType.analog, i);

            var config = new HomeAssistantNumberConfig(
                    haDevice,
                    determineAnalogName(device, i),
                    valueTopic,
                    valueTopic,
                    null,
                    determineAnalogIcon(device, i),
                    0,
                    255,
                    "slider",
                    "analog_" + i,
                    device.getSerialNumber() + "_analog_" + i,
                    true);
            mqttService.send(configTopic, config);
        }
    }

    private void addBrightnessDevice(MqttSettings settings, Device device, HomeAssistantDevice haDevice) {
        var configTopic = configTopicFor(settings, device, "number", "brightness", 0);
        var valueTopic = topicHelper.valueTopic(device.getSerialNumber(), MqttTopicHelper.ValueType.brightness, 0);

        var config = new HomeAssistantNumberConfig(
                haDevice,
                "Brightness",
                valueTopic,
                valueTopic,
                null,
                "mdi:brightness-percent",
                0,
                100,
                "slider",
                "brightness",
                device.getSerialNumber() + "_brightness",
                true);
        mqttService.send(configTopic, config);
    }

    private void addLogoLight(MqttSettings settings, Device device, HomeAssistantDevice haDevice) {
        var configTopic = lightTopicFor(settings, device, "logo");
        var valueTopic = topicHelper.lightTopic(device.getSerialNumber(), MqttTopicHelper.ColorType.logo, 0);

        var config = new HomeAssistantLightConfig(
                haDevice,
                "Logo Light",
                device.getSerialNumber() + "_logo",
                valueTopic,
                "mdi:lightbulb"
        );
        mqttService.send(configTopic, config);
    }

    private void addButtons(MqttSettings settings, Device device, HomeAssistantDevice haDevice) {
        for (var i = 0; i < device.getDeviceType().getButtonCount(); i++) {
            var configTopic = configTopicFor(settings, device, "binary_sensor", "button", i);
            var valueTopic = topicHelper.actionTopic(device.getSerialNumber(), MqttTopicHelper.ActionType.button, i);

            var config = new HomeAssistantButtonConfig(
                    haDevice,
                    valueTopic,
                    "Button " + (i + 1),
                    device.getSerialNumber() + "_button_" + i
            );
            mqttService.send(configTopic, config);
        }
    }

    private String configTopicFor(MqttSettings settings, Device device, String domain, String type, int idx) {
        return StringUtils.joinWith("/",
                settings.homeAssistantBaseTopic(),
                domain,
                device.getSerialNumber().toLowerCase(),
                device.getSerialNumber().toLowerCase() + "_" + type + "_" + idx,
                "config"
        );
    }

    private String lightTopicFor(MqttSettings settings, Device device, String name) {
        return StringUtils.joinWith("/",
                settings.homeAssistantBaseTopic(),
                "light",
                device.getSerialNumber().toLowerCase() + "_" + name,
                "light",
                "config"
        );
    }

    private String determineAnalogIcon(Device device, int i) {
        var buttonCount = device.getDeviceType().getButtonCount();
        if (i < buttonCount) {
            return "mdi:knob";
        }
        return "mdi:tune-vertical-variant";
    }

    private String determineAnalogName(Device device, int i) {
        var buttonCount = device.getDeviceType().getButtonCount();
        if (i < buttonCount) {
            return "Button " + (i + 1);
        }
        return "Slider " + (i - buttonCount + 1);
    }

    record HomeAssistantNumberConfig(
            HomeAssistantDevice device,
            String name,
            String command_topic,
            String state_topic,
            @Nullable String device_class,
            String icon,
            int min, // 0
            int max, // 255
            String mode, // slider
            String object_id,
            String unique_id,
            boolean retain // true
    ) {
    }

    record HomeAssistantLightConfig(
            HomeAssistantDevice device,
            String name,
            String object_id,
            String unique_id,
            String command_topic,
            String icon,

            // Pre-set
            String schema,
            String command_off_template,
            String command_on_template,
            String red_template,
            String green_template,
            String blue_template,
            String state_template,
            String state_topic,
            List<String> supported_color_modes,
            boolean retain
    ) {
        HomeAssistantLightConfig(HomeAssistantDevice device, String name, String unique_id, String command_topic, String icon) {
            this(device, name, unique_id, unique_id, command_topic, icon,
                    "template",
                    "#000000",
                    "#{{ '%02x%02x%02x' | format(" + asInt("red") + ", " + asInt("green") + ", " + asInt("blue") + ") }}",
                    "{{value[1:3] | int(0, 16)}}",
                    "{{value[3:5] | int(0, 16)}}",
                    "{{value[5:7] | int(0, 16)}}",
                    "{{ 'on' if value != '#000000' else 'off' }}",
                    command_topic,
                    List.of("rgb"),
                    true
            );
        }

        private static String asInt(String name) {
            return name + " | int(255) if " + name + " is defined else 255";
        }
    }

    record HomeAssistantButtonConfig( // Is actually a binary sensor
                                      HomeAssistantDevice device,
                                      String state_topic,
                                      String name,
                                      String object_id,
                                      String unique_id,

                                      String command_template,
                                      String icon,
                                      String payload_off,
                                      String payload_on
    ) {
        HomeAssistantButtonConfig(HomeAssistantDevice device, String command_topic, String name, String object_id) {
            this(device, command_topic, name, object_id, object_id,
                    "click",
                    "mdi:toggle-switch",
                    "release",
                    "click");
        }
    }

    record HomeAssistantDevice(
            String hw_version,
            List<String> identifiers,
            String manufacturer,
            String model,
            String name,
            String serial_number
    ) {
    }
}
