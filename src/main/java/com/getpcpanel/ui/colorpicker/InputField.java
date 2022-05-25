package com.getpcpanel.ui.colorpicker;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.IntegerPropertyBase;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.property.StringPropertyBase;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Control;

abstract class InputField extends Control {
    public static final int DEFAULT_PREF_COLUMN_COUNT = 12;

    private final BooleanProperty editable = new SimpleBooleanProperty(this, "editable", true);

    public final boolean isEditable() {
        return editable.getValue();
    }

    public final void setEditable(boolean value) {
        editable.setValue(value);
    }

    public final BooleanProperty editableProperty() {
        return editable;
    }

    private final StringProperty promptText = new StringPropertyBase("") {
        @Override
        protected void invalidated() {
            var txt = get();
            if (txt != null && txt.contains("\n")) {
                txt = txt.replace("\n", "");
                set(txt);
            }
        }

        @Override
        public Object getBean() {
            return InputField.this;
        }

        @Override
        public String getName() {
            return "promptText";
        }
    };

    public final StringProperty promptTextProperty() {
        return promptText;
    }

    public final String getPromptText() {
        return promptText.get();
    }

    public final void setPromptText(String value) {
        promptText.set(value);
    }

    private final IntegerProperty prefColumnCount = new IntegerPropertyBase(DEFAULT_PREF_COLUMN_COUNT) {
        private int oldValue = get();

        @Override
        protected void invalidated() {
            var value = get();
            if (value < 0) {
                if (isBound())
                    unbind();
                set(oldValue);
                throw new IllegalArgumentException("value cannot be negative.");
            }
            oldValue = value;
        }

        @Override
        public Object getBean() {
            return InputField.this;
        }

        @Override
        public String getName() {
            return "prefColumnCount";
        }
    };

    public final IntegerProperty prefColumnCountProperty() {
        return prefColumnCount;
    }

    public final int getPrefColumnCount() {
        return prefColumnCount.getValue();
    }

    public final void setPrefColumnCount(int value) {
        prefColumnCount.setValue(value);
    }

    private final ObjectProperty<EventHandler<ActionEvent>> onAction = new ObjectPropertyBase<>() {
        @Override
        protected void invalidated() {
            setEventHandler(ActionEvent.ACTION, get());
        }

        @Override
        public Object getBean() {
            return InputField.this;
        }

        @Override
        public String getName() {
            return "onAction";
        }
    };

    public final ObjectProperty<EventHandler<ActionEvent>> onActionProperty() {
        return onAction;
    }

    public final EventHandler<ActionEvent> getOnAction() {
        return onActionProperty().get();
    }

    public final void setOnAction(EventHandler<ActionEvent> value) {
        onActionProperty().set(value);
    }

    protected InputField() {
        getStyleClass().setAll("input-field");
    }
}
