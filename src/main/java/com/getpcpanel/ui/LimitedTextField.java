package com.getpcpanel.ui;

import java.util.Objects;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.control.TextField;

public class LimitedTextField extends TextField {
    private final IntegerProperty maxLength;

    public LimitedTextField(int limit) {
        maxLength = new SimpleIntegerProperty(limit);
    }

    public IntegerProperty maxLengthProperty() {
        return maxLength;
    }

    public final Integer getMaxLength() {
        return maxLength.getValue();
    }

    public final void setMaxLength(Integer maxLength) {
        Objects.requireNonNull(maxLength, "Max length cannot be null, -1 for no limit");
        this.maxLength.setValue(maxLength);
    }

    @Override
    public void replaceText(int start, int end, String insertedText) {
        if (getMaxLength() <= 0) {
            super.replaceText(start, end, insertedText);
        } else {
            var currentText = (getText() == null) ? "" : getText();
            var finalText = currentText.substring(0, start) + insertedText + currentText.substring(end);
            var numberOfexceedingCharacters = finalText.length() - getMaxLength();
            if (numberOfexceedingCharacters <= 0) {
                super.replaceText(start, end, insertedText);
            } else {
                var cutInsertedText = insertedText.substring(
                        0,
                        insertedText.length() - numberOfexceedingCharacters);
                super.replaceText(start, end, cutInsertedText);
            }
        }
    }
}
