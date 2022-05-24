package colorpicker;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Skin;
import javafx.scene.paint.Color;

class WebColorField extends InputField {
    private final ObjectProperty<Color> value = new SimpleObjectProperty<>(this, "value");

    public final Color getValue() {
        return value.get();
    }

    public final void setValue(Color value) {
        this.value.set(value);
    }

    public final ObjectProperty<Color> valueProperty() {
        return value;
    }

    public WebColorField() {
        getStyleClass().setAll("webcolor-field");
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new WebColorFieldSkin(this);
    }
}
