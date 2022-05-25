package com.getpcpanel.ui.colorpicker;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;

public class HueSlider extends Pane {
    private static final int MAX_HUE = 255;

    private final IntegerProperty hue = new SimpleIntegerProperty();

    public HueSlider() {
        getStyleClass().add("color-bar");
        setBackground(new Background(new BackgroundFill(createHueGradient(),
                CornerRadii.EMPTY, Insets.EMPTY)));
        setMinHeight(20.0D);
        var colorBarIndicator = new Circle(10.0D);
        colorBarIndicator.setFill(Paint.valueOf("#25262A"));
        colorBarIndicator.setId("color-bar-indicator");
        colorBarIndicator.setMouseTransparent(true);
        colorBarIndicator.setCache(true);
        colorBarIndicator.layoutXProperty().bind(hue.multiply(widthProperty()).divide(MAX_HUE));
        EventHandler<MouseEvent> barMouseHandler = event -> {
            var x = event.getX();
            hue.set((int) (clamp(x / getWidth()) * 255.0D));
        };
        setOnMouseDragged(barMouseHandler);
        setOnMousePressed(barMouseHandler);
        getChildren().setAll(colorBarIndicator);
        colorBarIndicator.setLayoutY(10.0D);
    }

    public IntegerProperty getHueProperty() {
        return hue;
    }

    public int getHue() {
        return hue.get();
    }

    public void setHue(int h) {
        hue.set(h);
    }

    private static LinearGradient createHueGradient() {
        var stops = new Stop[255];
        for (var y = 0; y < 255; y++) {
            var offset = 1.0D - 0.00392156862745098D * y;
            var h = (int) (y / 255.0D * 360.0D);
            stops[y] = new Stop(offset, Color.hsb(h, 1.0D, 1.0D));
        }
        return new LinearGradient(1.0D, 0.0D, 0.0D, 0.0D, true, CycleMethod.NO_CYCLE, stops);
    }

    static double clamp(double value) {
        return (value < 0.0D) ? 0.0D : ((value > 1.0D) ? 1.0D : value);
    }
}
