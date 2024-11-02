package com.getpcpanel.ui;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Component;

import com.getpcpanel.profile.MqttSettings;
import com.getpcpanel.profile.Save;
import com.getpcpanel.spring.Prototype;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import lombok.RequiredArgsConstructor;

@Component
@Prototype
@RequiredArgsConstructor
public class MqttSettingsDialog {
    @FXML private CheckBox enabled;
    @FXML private TextField host;
    @FXML private TextField port;
    @FXML private TextField username;
    @FXML private TextField password;
    @FXML private CheckBox secure;
    @FXML private TextField baseTopic;
    @FXML private CheckBox enableHomeAssistantDiscovery;
    @FXML private TextField homeAssistantBaseTopic;

    public void save(Save save) {
        save.setMqtt(new MqttSettings(
                enabled.isSelected(),
                host.getText(),
                NumberUtils.toInt(port.getText(), 1883),
                username.getText(),
                password.getText(),
                secure.isSelected(),
                baseTopic.getText(),
                enableHomeAssistantDiscovery.isSelected(),
                homeAssistantBaseTopic.getText()
        ));
    }

    public void populate(Save save) {
        if (save.getMqtt() != null) {
            populate(save.getMqtt());
        } else {
            populate(MqttSettings.DEFAULT);
        }
    }

    private void populate(@Nonnull MqttSettings settings) {
        enabled.setSelected(settings.enabled());
        host.setText(settings.host());
        port.setText(settings.port().toString());
        username.setText(settings.username());
        password.setText(settings.password());
        secure.setSelected(settings.secure());
        baseTopic.setText(settings.baseTopic());
        enableHomeAssistantDiscovery.setSelected(settings.homeAssistantDiscovery());
    }
}
