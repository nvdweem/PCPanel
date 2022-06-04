package com.getpcpanel.ui;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import one.util.streamex.StreamEx;

public class PickProcessesController {
    public enum PickType {
        file, soundSource, process
    }

    @FXML private VBox pickRows;
    private PickType pickType;

    public List<String> getSelection() {
        return StreamEx.of(pickRows.getChildren()).select(Pane.class).map(pane -> pane.getChildren().get(0)).select(TextField.class).map(TextField::getText).filter(StringUtils::isNotBlank).toList();
    }

    public void setSelection(PickType type, List<String> volData) {
        pickType = type;
        StreamEx.of(volData).map(StringUtils::trimToNull).nonNull().map(this::createProcessRow).forEach(pickRows.getChildren()::add);
        pickRows.getChildren().add(createProcessRow(""));
    }

    private void removeEmptyIfNotLast() {
        var toRemove = StreamEx.of(pickRows.getChildren())
                               .select(Pane.class)
                               .filter(ar -> StringUtils.isBlank(((TextField) ar.getChildren().get(0)).getText()))
                               .filter(ar -> !ar.equals(pickRows.getChildren().get(pickRows.getChildren().size() - 1)))
                               .toList();
        toRemove.forEach(pickRows.getChildren()::remove);
    }

    private void ensureLastEmpty() {
        if (pickRows.getChildren().isEmpty()) {
            pickRows.getChildren().add(createProcessRow(""));
        } else {
            var pane = (Pane) pickRows.getChildren().get(pickRows.getChildren().size() - 1);
            if (!StringUtils.isBlank(((TextField) pane.getChildren().get(0)).getText())) {
                pickRows.getChildren().add(createProcessRow(""));
            }
        }
    }

    private Pane createProcessRow(String value) {
        var pane = new HBox();

        var textField = new TextField();
        textField.setPromptText("Process");
        textField.setText(value);

        var button = new Button("...");
        button.setPrefSize(34, 31);
        button.setOnAction(e -> {
            switch (pickType) {
                case file -> UIHelper.showFilePicker("Application", textField);
                case soundSource -> showAppFinder(true, textField);
                case process -> showAppFinder(false, textField);
            }
            ensureLastEmpty();
        });
        var clear = new Button("X");
        clear.setPrefSize(34, 31);
        clear.setOnAction(e -> {
            textField.setText("");
            removeEmptyIfNotLast();
        });

        textField.setOnKeyReleased(e -> ensureLastEmpty());
        textField.focusedProperty().addListener((o, oldPropertyValue, newPropertyValue) -> {
            if (!newPropertyValue) {
                removeEmptyIfNotLast();
            }
        });

        HBox.setHgrow(textField, Priority.ALWAYS);
        pane.getChildren().add(textField);
        pane.getChildren().add(button);
        pane.getChildren().add(clear);
        return pane;
    }

    private void showAppFinder(boolean sound, TextField textField) {
        var afd = new AppFinderDialog(null, sound);
        var afdStage = new Stage();
        afd.start(afdStage, true);

        Optional.ofNullable(StringUtils.trimToNull(afd.getProcessName())).ifPresent(textField::setText);
    }
}
