package com.getpcpanel.mqtt;

import static com.getpcpanel.mqtt.MqttDeviceColorService.EFFECT_NONE;
import static com.getpcpanel.mqtt.MqttDeviceColorService.EFFECT_STOP_OVERRIDE;

import java.util.List;

import javax.annotation.Nonnull;
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

    public void clearAll(MqttSettings settings) {
        var topic = StringUtils.joinWith("/", settings.homeAssistant().baseTopic(), "+", "pcpanel", "#");
        mqttService.removeAll(topic);
    }

    public void discover(MqttSettings settings, Device device) {
        var haDevice = buildDevice(device);
        var availability = buildAvailability(settings);

        addAnalogValueConfigs(settings, device, haDevice, availability);
        addBrightnessDevice(settings, device, haDevice, availability);
        addLights(settings, device, haDevice, availability);
        addButtons(settings, device, haDevice, availability);
    }

    private void addLights(MqttSettings settings, Device device, HomeAssistantDevice haDevice, @Nullable HomeAssistantAvailability availability) {
        addLogoLight(settings, device, haDevice, availability);
        addControlLights(settings, device, haDevice, availability);
    }

    private void addControlLights(MqttSettings settings, Device device, HomeAssistantDevice haDevice, @Nullable HomeAssistantAvailability availability) {
        for (var i = 0; i < device.getDeviceType().getAnalogCount(); i++) {
            var buttonCount = device.getDeviceType().getButtonCount();
            var type = i < buttonCount ? MqttTopicHelper.ColorType.dial : MqttTopicHelper.ColorType.slider;
            var idx = i < buttonCount ? i : i - buttonCount;

            addControlLightConfig(settings, device, haDevice, availability, i, type, idx);
            if (type == MqttTopicHelper.ColorType.slider) {
                addSliderLabelLightConfig(settings, device, haDevice, availability, idx, type);
            }
        }
    }

    private void addControlLightConfig(MqttSettings settings, Device device, HomeAssistantDevice haDevice, @Nullable HomeAssistantAvailability availability, int i, MqttTopicHelper.ColorType type, int idx) {
        var controlConfigTopic = lightTopicFor(settings, device, "control_" + i);
        var controlValueTopic = topicHelper.lightTopic(device.getSerialNumber(), type, idx);

        var config = new HomeAssistantLightConfig(
                haDevice, availability,
                StringUtils.capitalize(type.name()) + " " + (idx + 1) + " Light",
                device.getSerialNumber() + "_" + type.name() + "_" + idx,
                controlValueTopic,
                determineAnalogIcon(device, i)
        );
        mqttService.send(controlConfigTopic, config, false);
    }

    private void addSliderLabelLightConfig(MqttSettings settings, Device device, HomeAssistantDevice haDevice, @Nullable HomeAssistantAvailability availability, int idx, MqttTopicHelper.ColorType type) {
        var labelConfigTopic = lightTopicFor(settings, device, "label_" + idx);
        var labelValueTopic = topicHelper.lightTopic(device.getSerialNumber(), MqttTopicHelper.ColorType.label, idx);

        var labelConfig = new HomeAssistantLightConfig(
                haDevice, availability,
                StringUtils.capitalize(type.name()) + " " + (idx + 1) + " Label Light",
                device.getSerialNumber() + "_label_" + idx,
                labelValueTopic,
                "mdi:label-variant"
        );
        mqttService.send(labelConfigTopic, labelConfig, false);
    }

    private void addAnalogValueConfigs(MqttSettings settings, Device device, HomeAssistantDevice haDevice, @Nullable HomeAssistantAvailability availability) {
        for (var i = 0; i < device.getDeviceType().getAnalogCount(); i++) {
            var configTopic = configTopicFor(settings, device, "number", "analog", i);
            var valueTopic = topicHelper.valueTopic(device.getSerialNumber(), MqttTopicHelper.ValueType.analog, i);

            var config = new HomeAssistantNumberConfig(
                    haDevice, availability,
                    determineAnalogName(device, i),
                    valueTopic,
                    valueTopic,
                    determineAnalogIcon(device, i),
                    255,
                    device.getSerialNumber() + "_analog_" + i
            );
            mqttService.send(configTopic, config, false);
        }
    }

    private void addBrightnessDevice(MqttSettings settings, Device device, HomeAssistantDevice haDevice, @Nullable HomeAssistantAvailability availability) {
        var configTopic = configTopicFor(settings, device, "number", "brightness", 0);
        var valueTopic = topicHelper.valueTopic(device.getSerialNumber(), MqttTopicHelper.ValueType.brightness, 0);

        var config = new HomeAssistantNumberConfig(
                haDevice, availability,
                "Brightness",
                valueTopic,
                valueTopic,
                "mdi:brightness-percent",
                100,
                device.getSerialNumber() + "_brightness"
        );
        mqttService.send(configTopic, config, false);
    }

    private void addLogoLight(MqttSettings settings, Device device, HomeAssistantDevice haDevice, @Nullable HomeAssistantAvailability availability) {
        if (!device.getDeviceType().isHasLogoLed()) {
            return;
        }

        var configTopic = lightTopicFor(settings, device, "logo");
        var valueTopic = topicHelper.lightTopic(device.getSerialNumber(), MqttTopicHelper.ColorType.logo, 0);

        var config = new HomeAssistantLightConfig(
                haDevice, availability,
                "Logo Light",
                device.getSerialNumber() + "_logo",
                valueTopic,
                "mdi:information-variant-circle"
        );
        mqttService.send(configTopic, config, false);
    }

    private void addButtons(MqttSettings settings, Device device, HomeAssistantDevice haDevice, @Nullable HomeAssistantAvailability availability) {
        for (var i = 0; i < device.getDeviceType().getButtonCount(); i++) {
            addButtonUpDown(settings, device, haDevice, availability, i);
            addButtonEvent(settings, device, haDevice, availability, i);
        }
    }

    private void addButtonUpDown(MqttSettings settings, Device device, HomeAssistantDevice haDevice, @Nullable HomeAssistantAvailability availability, int i) {
        var configTopic = configTopicFor(settings, device, "binary_sensor", "button", i);
        var valueTopic = topicHelper.buttonUpDownTopic(device.getSerialNumber(), MqttTopicHelper.ActionType.button, i);

        var upDownConfig = new HomeAssistantButtonConfig(
                haDevice, availability,
                valueTopic,
                "Button " + (i + 1),
                device.getSerialNumber() + "_button_" + i
        );
        mqttService.send(configTopic, upDownConfig, false);
    }

    private void addButtonEvent(MqttSettings settings, Device device, HomeAssistantDevice haDevice, @Nullable HomeAssistantAvailability availability, int i) {
        var eventConfigTopic = configTopicFor(settings, device, "event", "button", i);
        var eventTopic = topicHelper.eventTopic(device.getSerialNumber(), MqttTopicHelper.ActionType.button, i);

        var eventConfig = new HomeAssistantButtonEventConfig(
                haDevice, availability,
                eventTopic,
                "Button " + (i + 1),
                device.getSerialNumber() + "_button_event_" + i
        );
        mqttService.send(eventConfigTopic, eventConfig, false);
    }

    private String configTopicFor(MqttSettings settings, Device device, String domain, String type, int idx) {
        return StringUtils.joinWith("/",
                settings.homeAssistant().baseTopic(),
                domain,
                "pcpanel",
                device.getSerialNumber().toLowerCase() + "_" + type + "_" + idx,
                "config"
        );
    }

    private String lightTopicFor(MqttSettings settings, Device device, String name) {
        return StringUtils.joinWith("/",
                settings.homeAssistant().baseTopic(),
                "light",
                "pcpanel",
                device.getSerialNumber().toLowerCase() + "_" + name,
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
            return "Dial " + (i + 1);
        }
        return "Slider " + (i - buttonCount + 1);
    }

    record HomeAssistantNumberConfig(
            HomeAssistantDevice device,
            @Nullable HomeAssistantAvailability availability,
            String name,
            String command_topic,
            String state_topic,
            String icon,
            int min, // 0
            int max, // 255
            String mode, // slider
            String object_id,
            String unique_id,
            boolean retain // true
    ) {
        HomeAssistantNumberConfig(HomeAssistantDevice device, @Nullable HomeAssistantAvailability availability, String name, String command_topic, String state_topic, String icon, int max, String object_id) {
            this(
                    device, availability,
                    name,
                    command_topic,
                    state_topic,
                    icon,
                    0,
                    max,
                    "slider",
                    object_id,
                    object_id,
                    true
            );
        }
    }

    record HomeAssistantLightConfig(
            HomeAssistantDevice device,
            @Nullable HomeAssistantAvailability availability,
            String name,
            String object_id,
            String unique_id,
            String state_topic,
            String icon,

            // Pre-set
            String command_topic,
            String brightness_command_topic,
            String brightness_state_topic,
            String effect_command_topic,
            String effect_state_topic,
            List<String> effect_list,
            String rgb_command_topic,
            String rgb_state_topic,
            String state_value_template,
            boolean retain
    ) {
        HomeAssistantLightConfig(HomeAssistantDevice device, @Nullable HomeAssistantAvailability availability, String name, String unique_id, String state_topic, String icon) {
            this(device, availability, name, unique_id, unique_id, state_topic, icon,

                    state_topic + "/cmd",
                    state_topic + "/brightness",
                    state_topic + "/brightness",
                    state_topic + "/effect",
                    state_topic + "/effect",
                    List.of(EFFECT_NONE, EFFECT_STOP_OVERRIDE),
                    state_topic + "/rgb",
                    state_topic + "/rgb",
                    "{{'OFF' if value == '#000000' else 'ON'}}",
                    true
            );
        }
    }

    record HomeAssistantButtonConfig( // Is actually a binary sensor
                                      HomeAssistantDevice device,
                                      @Nullable HomeAssistantAvailability availability,
                                      String state_topic,
                                      String name,
                                      String object_id,
                                      String unique_id,

                                      String command_template,
                                      String icon,
                                      String payload_off,
                                      String payload_on
    ) {
        HomeAssistantButtonConfig(HomeAssistantDevice device, @Nullable HomeAssistantAvailability availability, String command_topic, String name, String object_id) {
            this(device, availability, command_topic, name, object_id, object_id,
                    "down",
                    "mdi:knob",
                    "up",
                    "down");
        }
    }

    record HomeAssistantButtonEventConfig(HomeAssistantDevice device,
                                          @Nullable HomeAssistantAvailability availability,
                                          String state_topic,
                                          String name,
                                          String object_id,

                                          String unique_id,
                                          String icon,
                                          String platform,
                                          List<String> event_types) {
        HomeAssistantButtonEventConfig(HomeAssistantDevice device, @Nullable HomeAssistantAvailability availability, String state_topic, String name, String object_id) {
            this(device, availability, state_topic, name, object_id,
                    object_id,
                    "mdi:knob",
                    "event",
                    List.of("click", "double_click")
            );
        }
    }

    @Nonnull
    private HomeAssistantDevice buildDevice(Device device) {
        return new HomeAssistantDevice(
                version,
                List.of(device.getSerialNumber()),
                "PCPanel Holdings, LLC",
                device.getDeviceType().getNiceName(),
                device.getSerialNumber(),
                device.getSerialNumber(),
                "Office"
        );
    }

    private @Nullable HomeAssistantAvailability buildAvailability(MqttSettings settings) {
        if (settings.homeAssistant().availability()) {
            return new HomeAssistantAvailability(
                    topicHelper.availabilityTopic(),
                    "online",
                    "offline",
                    "{{'offline' if (value is undefined or value != 'online') else 'online'}}"
            );
        }
        return null;
    }

    record HomeAssistantDevice(
            String sw_version,
            List<String> identifiers,
            String manufacturer,
            String model,
            String name,
            String serial_number,
            String suggested_area
    ) {
    }

    record HomeAssistantAvailability(
            String topic,
            String payload_available,
            String payload_not_available,
            String value_template
    ) {
    }
}
