package com.getpcpanel.ui;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.getpcpanel.spring.Prototype;
import com.getpcpanel.util.Images;

import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleLongProperty;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import one.util.streamex.StreamEx;

@Component
@Prototype
@RequiredArgsConstructor
public class PickProcessesController {
    public enum PickType {
        file, soundSource, process
    }

    private final FxHelper fxHelper;
    @Setter private boolean single;

    @FXML private VBox pickRows;
    @Setter private PickType pickType;

    public void initialize() {
        ensureLastEmpty();
    }

    public List<String> getSelection() {
        return StreamEx.of(pickRows.getChildren()).select(Pane.class).map(pane -> pane.getChildren().get(0)).select(TextField.class).map(TextField::getText).filter(StringUtils::isNotBlank).toList();
    }

    public void setSelection(Collection<String> volData) {
        StreamEx.of(volData).map(StringUtils::trimToNull).nonNull().map(this::createProcessRow).forEach(pickRows.getChildren()::add);
        removeEmptyIfNotLast();

        if (!single) {
            pickRows.getChildren().add(createProcessRow(""));
            ensureLastEmpty();
        }
    }

    public void setDisable(boolean disable) {
        StreamEx.of(pickRows.getChildren()).select(Pane.class).flatCollection(Pane::getChildren).forEach(ctrl -> ctrl.setDisable(disable));
    }

    public Observable getObservable() {
        var binding = new SimpleLongProperty();
        pickRows.getChildren().addListener((ListChangeListener.Change<?> c) -> {
            binding.unbind();
            var values = StreamEx.of(pickRows.getChildren()).select(Pane.class).map(pane -> pane.getChildren().get(0)).select(TextField.class).map(TextField::textProperty).toArray(Observable[]::new);
            binding.bind(Bindings.createLongBinding(System::currentTimeMillis, values));
        });
        return binding;
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
        if (single)
            return;

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

        var button = new Button("", Images.magnify());
        button.setPrefSize(34, 31);
        button.setOnAction(e -> {
            switch (pickType) {
                case file -> UIHelper.showFilePicker("Application", textField);
                case soundSource -> showAppFinder(true, textField);
                case process -> showAppFinder(false, textField);
            }
            ensureLastEmpty();
        });
        var clear = new Button("", Images.delete());
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
        var afd = fxHelper.buildAppFinderDialog(null, sound);
        var afdStage = new Stage();
        afd.start(afdStage, true);

        Optional.ofNullable(StringUtils.trimToNull(afd.getProcessName())).ifPresent(textField::setText);
    }
}
