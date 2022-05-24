package colorpicker;

import com.sun.javafx.event.EventDispatchChainImpl;

import javafx.beans.InvalidationListener;
import javafx.event.EventDispatchChain;
import javafx.scene.Node;
import javafx.scene.control.Skin;
import javafx.scene.control.TextField;

abstract class InputFieldSkin implements Skin<InputField> {
    protected InputField control;

    private InnerTextField textField;

    private final InvalidationListener InputFieldFocusListener;

    private final InvalidationListener InputFieldStyleClassListener;

    protected InputFieldSkin(InputField control) {
        this.control = control;
        textField = new InnerTextField() {
            @Override
            public void replaceText(int start, int end, String text) {
                var t = (textField.getText() == null) ? "" : textField.getText();
                t = t.substring(0, start) + text + t.substring(end);
                if (accept(t))
                    super.replaceText(start, end, text);
            }

            @Override
            public void replaceSelection(String text) {
                var t = (textField.getText() == null) ? "" : textField.getText();
                var start = Math.min(textField.getAnchor(), textField.getCaretPosition());
                var end = Math.max(textField.getAnchor(), textField.getCaretPosition());
                t = t.substring(0, start) + text + t.substring(end);
                if (accept(t))
                    super.replaceSelection(text);
            }
        };
        textField.setId("input-text-field");
        textField.setFocusTraversable(false);
        control.getStyleClass().addAll(textField.getStyleClass());
        textField.getStyleClass().setAll(control.getStyleClass());
        control.getStyleClass().addListener(InputFieldStyleClassListener = (observable -> textField.getStyleClass().setAll(control.getStyleClass())));
        textField.promptTextProperty().bind(control.promptTextProperty());
        textField.prefColumnCountProperty().bind(control.prefColumnCountProperty());
        textField.textProperty().addListener(observable -> updateValue());
        control.focusedProperty().addListener(InputFieldFocusListener = (observable -> textField.handleFocus(control.isFocused())));
        updateText();
    }

    @Override
    public InputField getSkinnable() {
        return control;
    }

    @Override
    public Node getNode() {
        return textField;
    }

    @Override
    public void dispose() {
        control.getStyleClass().removeListener(InputFieldStyleClassListener);
        control.focusedProperty().removeListener(InputFieldFocusListener);
        textField = null;
    }

    protected TextField getTextField() {
        return textField;
    }

    protected abstract boolean accept(String paramString);

    protected abstract void updateText();

    protected abstract void updateValue();

    private class InnerTextField extends TextField {
        private InnerTextField() {
        }

        public void handleFocus(boolean b) {
            setFocused(b);
        }

        @Override
        public EventDispatchChain buildEventDispatchChain(EventDispatchChain tail) {
            var eventDispatchChainImpl = new EventDispatchChainImpl();
            eventDispatchChainImpl.append(textField.getEventDispatcher());
            return eventDispatchChainImpl;
        }
    }
}
