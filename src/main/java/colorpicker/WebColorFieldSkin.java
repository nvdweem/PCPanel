package colorpicker;

import javafx.beans.InvalidationListener;
import javafx.geometry.NodeOrientation;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import lombok.extern.log4j.Log4j2;
import util.Util;

import java.util.Locale;

@Log4j2
class WebColorFieldSkin extends InputFieldSkin {
    private final InvalidationListener integerFieldValueListener;

    private boolean noChangeInValue;

    public WebColorFieldSkin(WebColorField control) {
        super(control);
        control.valueProperty().addListener(integerFieldValueListener = (observable -> updateText()));
        getTextField().setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
    }

    @Override
    public WebColorField getSkinnable() {
        return (WebColorField) control;
    }

    @Override
    public Node getNode() {
        return getTextField();
    }

    @Override
    public void dispose() {
        ((WebColorField) control).valueProperty().removeListener(integerFieldValueListener);
        super.dispose();
    }

    @Override
    protected boolean accept(String text) {
        if (text.length() == 0)
            return true;
        return text.matches("#[a-fA-F0-9]{0,6}") || text.matches("[a-fA-F0-9]{0,6}");
    }

    @Override
    protected void updateText() {
        Color color = ((WebColorField) control).getValue();
        if (color == null)
            color = Color.BLACK;
        getTextField().setText(Util.formatHexString(color));
    }

    @Override
    protected void updateValue() {
        if (noChangeInValue)
            return;
        Color value = ((WebColorField) control).getValue();
        String text = (getTextField().getText() == null) ? "" : getTextField().getText().trim().toUpperCase(Locale.ROOT);
        if (text.matches("#[A-F0-9]{6}") || text.matches("[A-F0-9]{6}"))
            try {
                Color newValue = (text.charAt(0) == '#') ? Color.web(text) : Color.web("#" + text);
                if (!newValue.equals(value)) {
                    ((WebColorField) control).setValue(newValue);
                } else {
                    noChangeInValue = true;
                    getTextField().setText(Util.formatHexString(newValue));
                    noChangeInValue = false;
                }
            } catch (IllegalArgumentException ex) {
                log.error("Failed to parse [{}]", text);
            }
    }
}
