package com.getpcpanel.ui;

import com.getpcpanel.cpp.AudioDevice;

import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.DragEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.util.Callback;

public class SoundDeviceImportFactory implements Callback<ListView<AudioDevice>, ListCell<AudioDevice>> {
    public static final DataFormat JAVA_FORMAT = new DataFormat("application/x-java-serialized-object");

    private static final String DROP_HINT_STYLE_ABOVE = "-fx-border-color: #eea82f; -fx-border-width: 2 0 0 0; -fx-padding: 1 7 3 7;";

    private static final String DROP_HINT_STYLE_BELLOW = "-fx-border-color: #eea82f; -fx-border-width: 0 0 2 0; -fx-padding: 3 7 1 7";

    private ListCell<AudioDevice> dropZone;

    private AudioDevice draggedItem;

    private final ListView<AudioDevice> listView;

    public SoundDeviceImportFactory(ListView<AudioDevice> listView) {
        this.listView = listView;
        setupListView(listView);
    }

    @Override
    public ListCell<AudioDevice> call(ListView<AudioDevice> listView) {
        var cell = new ListCell<AudioDevice>() {
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
        cell.setOnDragDetected(event -> dragDetected(event, cell, listView));
        cell.setOnDragOver(event -> dragOver(event, cell, listView));
        cell.setOnDragDropped(event -> drop(event, cell, listView));
        cell.setOnDragDone(event -> clearDropLocation());
        cell.setOnDragExited(event -> clearDropLocation());
        return cell;
    }

    private void dragDetected(MouseEvent event, ListCell<AudioDevice> treeCell, ListView<AudioDevice> treeView) {
        draggedItem = treeCell.getItem();
        var db = treeCell.startDragAndDrop(TransferMode.MOVE);
        var content = new ClipboardContent();
        content.put(JAVA_FORMAT, draggedItem);
        db.setContent(content);
        db.setDragView(treeCell.snapshot(null, null));
        event.consume();
    }

    private void dragOver(DragEvent event, ListCell<AudioDevice> treeCell, ListView<AudioDevice> treeView) {
        if (!event.getDragboard().hasContent(JAVA_FORMAT))
            return;
        var thisItem = treeCell.getItem();
        var dropContent = (AudioDevice) event.getDragboard().getContent(JAVA_FORMAT);
        if (event.getGestureSource().getClass().getEnclosingClass().getName().contains("SoundDeviceImportFactory")) {
            if (thisItem == draggedItem)
                return;
        } else if (treeView.getItems().contains(dropContent)) {
            return;
        }
        event.acceptTransferModes(TransferMode.MOVE);
        if (thisItem == null)
            return;
        clearDropLocation();
        dropZone = treeCell;
        if (event.getY() < treeCell.getHeight() / 2.0D) {
            dropZone.setStyle(DROP_HINT_STYLE_ABOVE);
        } else {
            dropZone.setStyle(DROP_HINT_STYLE_BELLOW);
        }
    }

    private void drop(DragEvent event, ListCell<AudioDevice> treeCell, ListView<AudioDevice> treeView) {
        var db = event.getDragboard();
        var success = false;
        if (!db.hasContent(JAVA_FORMAT))
            return;
        var thisItem = treeCell.getItem();
        var index = listView.getItems().indexOf(thisItem);
        if (DROP_HINT_STYLE_BELLOW.equals(treeCell.getStyle()))
            index++;
        if (index == -1)
            index = listView.getItems().size();
        if (event.getGestureSource().getClass().getEnclosingClass().getName().contains("SoundDeviceImportFactory")) {
            var moverLocation = listView.getItems().indexOf(draggedItem);
            if (index > moverLocation)
                index--;
            listView.getItems().remove(draggedItem);
            listView.getItems().add(index, draggedItem);
            treeView.getSelectionModel().select(draggedItem);
        } else {
            listView.getItems().add(index, (AudioDevice) db.getContent(JAVA_FORMAT));
        }
        event.setDropCompleted(success);
        clearDropLocation();
    }

    private void clearDropLocation() {
        if (dropZone != null)
            dropZone.setStyle("");
    }

    private static void setupListView(ListView<AudioDevice> listView) {
        listView.setOnDragOver(event -> {
            if (!event.getDragboard().hasContent(JAVA_FORMAT) || !listView.getItems().isEmpty())
                return;
            var dropContent = (AudioDevice) event.getDragboard().getContent(JAVA_FORMAT);
            if (listView.getItems().contains(dropContent))
                return;
            event.acceptTransferModes(TransferMode.MOVE);
        });
        listView.setOnDragDropped(event -> {
            var db = event.getDragboard();
            var success = false;
            if (!db.hasContent(JAVA_FORMAT) || !listView.getItems().isEmpty())
                return;
            var index = listView.getItems().size();
            listView.getItems().add(index, (AudioDevice) db.getContent(JAVA_FORMAT));
            event.setDropCompleted(success);
        });
    }
}
