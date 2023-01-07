package com.getpcpanel.ui;

import java.util.List;

import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Component;

import com.getpcpanel.profile.OSCConnectionInfo;
import com.getpcpanel.spring.Prototype;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import one.util.streamex.StreamEx;

@Component
@Prototype
@RequiredArgsConstructor
public class OSCSettingsDialog {
    @FXML public VBox connectHostPorts;

    public void addConnectHostPort(MouseEvent ignored) {
        add("localhost", 8000);
    }

    private void add(String host, int port) {
        var target = new HBox();
        target.setAlignment(Pos.CENTER_LEFT);
        var hostLabel = new Label("Host");
        var hostField = new TextField(host);
        var portLabel = new Label("Port");
        var portField = new TextField(String.valueOf(port));
        var deleteBtn = new Button("X");

        target.getChildren().addAll(hostLabel, hostField, portLabel, portField, deleteBtn);
        HBox.setMargin(hostField, new Insets(0, 15, 0, 5));
        HBox.setMargin(portLabel, new Insets(0, 5, 0, 5));
        connectHostPorts.getChildren().add(target);

        deleteBtn.setOnMouseClicked(e -> connectHostPorts.getChildren().remove(target));
    }

    public List<OSCConnectionInfo> getConnections() {
        return StreamEx.of(connectHostPorts.getChildren())
                       .map(node -> ((HBox) node).getChildren())
                       .mapToEntry(c -> ((TextField) c.get(1)).getText(), c -> ((TextField) c.get(3)).getText())
                       .mapValues(val -> NumberUtils.toInt(val, -1))
                       .filterValues(val -> val != -1)
                       .mapKeyValue(OSCConnectionInfo::new)
                       .toList();
    }

    public void setConnections(List<OSCConnectionInfo> oscConnections) {
        if (oscConnections == null) {
            return;
        }
        oscConnections.forEach(c -> add(c.host(), c.port()));
    }
}
