package com.getpcpanel.ui;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.DeviceSet;
import com.getpcpanel.spring.Prototype;

import javafx.fxml.FXML;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import one.util.streamex.StreamEx;

@Component
@Prototype
@RequiredArgsConstructor
public class AdvancedDevices {
    private final FxHelper loader;

    @FXML private VBox target;
    private final List<AdvancedDevice> controllers = new ArrayList<>();
    @Setter private boolean allowRemove;
    private boolean onlyMedia;

    public void add() {
        add("", "", "", "", "");
    }

    public void set(String name, String mediaPlayback, String mediaRecord, String communicationPlayback, String communicationRecord) {
        controllers.clear();
        target.getChildren().clear();

        add(name, mediaPlayback, mediaRecord, communicationPlayback, communicationRecord);
    }

    public void add(DeviceSet entry) {
        add(entry.name(), entry.mediaPlayback(), entry.mediaRecord(), entry.communicationPlayback(), entry.communicationRecord());
    }

    public void add(String name, String mediaPlayback, String mediaRecord, String communicationPlayback, String communicationRecord) {
        var device = loader.open(AdvancedDevice.class, null);
        device.set(name, mediaPlayback, mediaRecord, communicationPlayback, communicationRecord);
        if (allowRemove) {
            device.removeCallback(() -> {
                target.getChildren().remove(device.getRoot());
                controllers.remove(device);
            });
        }

        target.getChildren().add(device.getRoot());
        device.setOnlyMedia(onlyMedia);
        controllers.add(device);
    }

    public List<DeviceSet> getEntries() {
        return StreamEx.of(controllers).map(AdvancedDevice::getEntry).toList();
    }

    public void setOnlyMedia(boolean onlyMedia) {
        this.onlyMedia = onlyMedia;
        controllers.forEach(c -> c.setOnlyMedia(onlyMedia));
    }
}
