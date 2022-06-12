package com.getpcpanel.ui;

import java.io.File;
import java.util.Optional;

import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public final class UIHelper {

    private UIHelper() {
    }

    public static void showFolderPicker(String title, TextField target) {
        pickFile(title, target)
                .map(f -> f.isFile() ? f.getParentFile() : f)
                .ifPresent(f -> target.setText(f.getAbsolutePath()));
    }

    public static void showFilePicker(String title, TextField target) {
        pickFile(title, target)
                .ifPresent(f -> target.setText(f.getAbsolutePath()));
    }

    private static Optional<File> pickFile(String title, TextField target) {
        var stage = (Stage) target.getScene().getWindow();
        var fileChooser = new FileChooser();
        fileChooser.setTitle(title);

        var current = new File(target.getText());
        fileChooser.setInitialFileName(current.isDirectory() ? "" : current.getName());
        fileChooser.setInitialDirectory(current.isDirectory() ? current : current.getParentFile());

        return Optional.ofNullable(fileChooser.showOpenDialog(stage));
    }
}
