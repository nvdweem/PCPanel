package com.getpcpanel.ui;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.DeviceSet;
import com.getpcpanel.cpp.AudioDevice;
import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.spring.Prototype;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import one.util.streamex.StreamEx;

@Component
@Prototype
@RequiredArgsConstructor
public class AdvancedDevice {
    private final ISndCtrl sndCtrl;
    @FXML private ComboBox<String> mediaPlayback;
    @FXML private ComboBox<String> mediaRecord;
    @FXML private ComboBox<String> communicationPlayback;
    @FXML private ComboBox<String> communicationRecord;
    @Getter @FXML private VBox root;
    @FXML private Button remove;

    public void initialize() {
        var allSoundDevices = sndCtrl.getDevices().stream().toList();
        var outputDevices = allSoundDevices.stream().filter(AudioDevice::isOutput).toList();
        var inputDevices = allSoundDevices.stream().filter(AudioDevice::isInput).toList();

        mediaPlayback.getItems().addAll(StreamEx.of(outputDevices).map(Object::toString).toList());
        mediaRecord.getItems().addAll(StreamEx.of(inputDevices).map(Object::toString).toList());
        communicationPlayback.getItems().addAll(StreamEx.of(outputDevices).map(Object::toString).toList());
        communicationRecord.getItems().addAll(StreamEx.of(inputDevices).map(Object::toString).toList());
    }

    public void set(String mediaPlayback, String mediaRecord, String communicationPlayback, String communicationRecord) {
        this.mediaPlayback.setValue(mediaPlayback);
        this.mediaRecord.setValue(mediaRecord);
        this.communicationPlayback.setValue(communicationPlayback);
        this.communicationRecord.setValue(communicationRecord);
    }

    public void removeCallback(Runnable callback) {
        remove.setOnAction(e -> callback.run());
        remove.setVisible(true);
    }

    public DeviceSet getEntry() {
        return new DeviceSet(mediaPlayback.getValue(), mediaRecord.getValue(), communicationPlayback.getValue(), communicationRecord.getValue());
    }
}
