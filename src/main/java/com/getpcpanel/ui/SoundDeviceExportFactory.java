package com.getpcpanel.ui;

import com.getpcpanel.util.SoundDevice;

import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.util.Callback;

public class SoundDeviceExportFactory implements Callback<ListView<SoundDevice>, ListCell<SoundDevice>> {
    private static final DataFormat JAVA_FORMAT = SoundDeviceImportFactory.JAVA_FORMAT;

    public SoundDeviceExportFactory(ListView<SoundDevice> listView) {
        setupListView(listView);
    }

    @Override
    public ListCell<SoundDevice> call(ListView<SoundDevice> listView) {
        ListCell<SoundDevice> cell = new ListCell<>() {
            @Override
            protected void updateItem(SoundDevice item, boolean empty) {
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

    private void dragDetected(MouseEvent event, ListCell<SoundDevice> treeCell) {
        var draggedItem = treeCell.getItem();
        var db = treeCell.startDragAndDrop(TransferMode.MOVE);
        var content = new ClipboardContent();
        content.put(JAVA_FORMAT, draggedItem);
        db.setContent(content);
        db.setDragView(treeCell.snapshot(null, null));
        event.consume();
    }

    private static void setupListView(ListView<SoundDevice> listView) {
        listView.setOnDragOver(event -> {
            if (!event.getDragboard().hasContent(JAVA_FORMAT))
                return;
            var dropContent = (SoundDevice) event.getDragboard().getContent(JAVA_FORMAT);
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
            listView.getItems().add(index, (SoundDevice) db.getContent(JAVA_FORMAT));
            event.setDropCompleted(success);
        });
    }
}
