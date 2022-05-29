package com.getpcpanel.ui;

import com.getpcpanel.cpp.AudioDevice;

import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.util.Callback;

public class SoundDeviceExportFactory implements Callback<ListView<AudioDevice>, ListCell<AudioDevice>> {
    private static final DataFormat JAVA_FORMAT = SoundDeviceImportFactory.JAVA_FORMAT;

    public SoundDeviceExportFactory(ListView<AudioDevice> listView) {
        setupListView(listView);
    }

    @Override
    public ListCell<AudioDevice> call(ListView<AudioDevice> listView) {
        ListCell<AudioDevice> cell = new ListCell<>() {
            @Override
            protected void updateItem(AudioDevice item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null) {
                    setText("");
                    return;
                }
                setText(item.toString());
            }
        };
        cell.setOnDragDetected(event -> dragDetected(event, cell));
        return cell;
    }

    private void dragDetected(MouseEvent event, ListCell<AudioDevice> treeCell) {
        var draggedItem = treeCell.getItem();
        var db = treeCell.startDragAndDrop(TransferMode.MOVE);
        var content = new ClipboardContent();
        content.put(JAVA_FORMAT, draggedItem);
        db.setContent(content);
        db.setDragView(treeCell.snapshot(null, null));
        event.consume();
    }

    private static void setupListView(ListView<AudioDevice> listView) {
        listView.setOnDragOver(event -> {
            if (!event.getDragboard().hasContent(JAVA_FORMAT))
                return;
            var dropContent = (AudioDevice) event.getDragboard().getContent(JAVA_FORMAT);
            if (listView.getItems().contains(dropContent))
                return;
            event.acceptTransferModes(TransferMode.MOVE);
        });
        listView.setOnDragDropped(event -> {
            var db = event.getDragboard();
            var success = false;
            if (!db.hasContent(JAVA_FORMAT))
                return;
            var index = 0;
            listView.getItems().add(index, (AudioDevice) db.getContent(JAVA_FORMAT));
            event.setDropCompleted(success);
        });
    }
}
