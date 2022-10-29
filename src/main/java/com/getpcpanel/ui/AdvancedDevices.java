package com.getpcpanel.ui;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.getpcpanel.spring.Prototype;

import javafx.fxml.FXML;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import one.util.streamex.StreamEx;

@Component
@Prototype
@RequiredArgsConstructor
public class AdvancedDevices {
    private final FxHelper loader;

    @FXML private VBox target;
    private final List<AdvancedDevice> controllers = new ArrayList<>();

    public void add(String mediaPlayback, String mediaRecord, String communicationPlayback, String communicationRecord) {
        var device = loader.open(AdvancedDevice.class);
        device.set(mediaPlayback, mediaRecord, communicationPlayback, communicationRecord);
        target.getChildren().add(device.getRoot());
        controllers.add(device);
    }

    public List<AdvancedDevice.Entry> getEntries() {
        return StreamEx.of(controllers).map(AdvancedDevice::getEntry).toList();
    }
}
