package com.getpcpanel.ui;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.DeviceSet;
import com.getpcpanel.cpp.AudioDevice;
import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.spring.Prototype;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import one.util.streamex.StreamEx;

@Component
@Prototype
@RequiredArgsConstructor
public class AdvancedDevice {
    private final ISndCtrl sndCtrl;
    @FXML private TextField name;
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

        mediaPlayback.getItems().addAll(StreamEx.of(outputDevices).map(Object::toString).prepend("").toList());
        mediaRecord.getItems().addAll(StreamEx.of(inputDevices).map(Object::toString).prepend("").toList());
        communicationPlayback.getItems().addAll(StreamEx.of(outputDevices).map(Object::toString).prepend("").toList());
        communicationRecord.getItems().addAll(StreamEx.of(inputDevices).map(Object::toString).prepend("").toList());
    }

    public void set(String name, String mediaPlayback, String mediaRecord, String communicationPlayback, String communicationRecord) {
        this.name.setText(name);
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
        return new DeviceSet(name.getText(), mediaPlayback.getValue(), mediaRecord.getValue(), communicationPlayback.getValue(), communicationRecord.getValue());
    }
}
