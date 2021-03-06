package com.getpcpanel.ui;

import java.util.Objects;

import com.getpcpanel.device.Device;
import com.getpcpanel.profile.SaveService;

import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

public class DeviceCell extends ListCell<Device> {
    private final SaveService saveService;
    private final ImageView imageView = new ImageView();
    private final LimitedTextField textField = new LimitedTextField(15);
    private final VBox vbox = new VBox(imageView);
    private final ListView<Device> listView;

    public static Callback<ListView<Device>, ListCell<Device>> buildFactory(SaveService saveService) {
        return cell -> new DeviceCell(saveService, cell);
    }

    public DeviceCell(SaveService saveService, ListView<Device> listView) {
        this.saveService = saveService;
        this.listView = listView;
        setContentDisplay(ContentDisplay.BOTTOM);
        vbox.getStylesheets().addAll(Objects.requireNonNull(getClass().getResource("/assets/1.css"), "Unable to find 1.css").toExternalForm());
        vbox.setAlignment(Pos.TOP_CENTER);
        textField.setOnAction(e -> {
            if (textField.getText().trim().isEmpty())
                return;
            commitEdit(getItem());
        });
        textField.setMaxWidth(listView.getWidth() - 30.0D);
        textField.setMaxHeight(30.0D);
        setOnMouseClicked(mouseClickedEvent -> {
            if (mouseClickedEvent.getButton() == MouseButton.PRIMARY && mouseClickedEvent.getClickCount() == 1)
                if (isEditing())
                    cancelEdit();
        });
    }

    @Override
    protected void updateItem(Device device, boolean empty) {
        super.updateItem(device, empty);
        if (empty || device == null) {
            setGraphic(null);
            setText("");
            setMaxHeight(-1.0D);
        } else {
            setGraphic(vbox);
            imageView.setImage(device.getPreviewImage());
            setText(device.getDisplayName());
            vbox.setMaxHeight(30.0D + imageView.getFitHeight());
        }
    }

    @Override
    public void startEdit() {
        super.startEdit();
        textField.setText(getText());
        vbox.getChildren().add(0, textField);
        setText("");
        textField.requestFocus();
        textField.selectAll();
    }

    @Override
    public void cancelEdit() {
        super.cancelEdit();
        vbox.getChildren().remove(0);
        setText(getItem().getDisplayName());
    }

    @Override
    public void commitEdit(Device device) {
        var newValue = textField.getText().trim();
        super.commitEdit(device);
        listView.getSelectionModel().select(device);
        setText(newValue);
        device.setDisplayName(newValue);
        saveService.save();
    }
}
