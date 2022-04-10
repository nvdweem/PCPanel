package colorpicker;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.control.Skin;

class IntegerField extends InputField {
    private final IntegerProperty value = new SimpleIntegerProperty(this, "value");

    public final int getValue() {
        return value.get();
    }

    public final void setValue(int value) {
        this.value.set(value);
    }

    public final IntegerProperty valueProperty() {
        return value;
    }

    private final IntegerProperty maxValue = new SimpleIntegerProperty(this, "maxValue", -1);

    public final int getMaxValue() {
        return maxValue.get();
    }

    public final void setMaxValue(int maxVal) {
        maxValue.set(maxVal);
    }

    public final IntegerProperty maxValueProperty() {
        return maxValue;
    }

    public IntegerField() {
        this(-1);
    }

    public IntegerField(int maxVal) {
        getStyleClass().setAll("integer-field");
        setMaxValue(maxVal);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new IntegerFieldSkin(this);
    }
}
