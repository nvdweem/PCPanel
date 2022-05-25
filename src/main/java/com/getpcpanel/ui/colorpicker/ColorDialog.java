package com.getpcpanel.ui.colorpicker;

import java.util.Objects;

import com.sun.javafx.scene.control.skin.resources.ControlResources;

import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;

public class ColorDialog extends HBox {
    private ColorRectPane colorRectPane;
    private final ObjectProperty<Color> customColorProperty = new SimpleObjectProperty<>(Color.TRANSPARENT);

    public ColorDialog(Color color) {
        getStyleClass().add("custom-color-dialog");
        buildUI();
        setCustomColor(Objects.requireNonNullElse(color, Color.BLACK));
    }

    public ColorDialog() {
        this(null);
    }

    private void buildUI() {
        colorRectPane = new ColorRectPane();
        var controlsPane = new ControlsPane();
        setHgrow(controlsPane, Priority.ALWAYS);
        getChildren().setAll(colorRectPane, controlsPane);
    }

    static String getString(String key) {
        return ControlResources.getString("ColorPicker." + key);
    }

    public ObjectProperty<Color> customColorProperty() {
        return customColorProperty;
    }

    public final void setCustomColor(Color color) {
        customColorProperty.set(color);
    }

    public Color getCustomColor() {
        return customColorProperty.get();
    }

    private class ColorRectPane extends HBox {
        private final Pane colorRect;
        private final Pane colorBar;
        private final Region colorRectIndicator;
        private boolean changeIsLocal;

        private final DoubleProperty hue = new SimpleDoubleProperty(-1.0D) {
            @Override
            protected void invalidated() {
                if (!changeIsLocal) {
                    changeIsLocal = true;
                    updateHSBColor();
                    changeIsLocal = false;
                }
            }
        };

        private final DoubleProperty sat = new SimpleDoubleProperty(-1.0D) {
            @Override
            protected void invalidated() {
                if (!changeIsLocal) {
                    changeIsLocal = true;
                    updateHSBColor();
                    changeIsLocal = false;
                }
            }
        };

        private final DoubleProperty bright = new SimpleDoubleProperty(-1.0D) {
            @Override
            protected void invalidated() {
                if (!changeIsLocal) {
                    changeIsLocal = true;
                    updateHSBColor();
                    changeIsLocal = false;
                }
            }
        };

        private final IntegerProperty red = new SimpleIntegerProperty(-1) {
            @Override
            protected void invalidated() {
                if (!changeIsLocal) {
                    changeIsLocal = true;
                    updateRGBColor();
                    changeIsLocal = false;
                }
            }
        };

        private final IntegerProperty green = new SimpleIntegerProperty(-1) {
            @Override
            protected void invalidated() {
                if (!changeIsLocal) {
                    changeIsLocal = true;
                    updateRGBColor();
                    changeIsLocal = false;
                }
            }
        };

        private final IntegerProperty blue = new SimpleIntegerProperty(-1) {
            @Override
            protected void invalidated() {
                if (!changeIsLocal) {
                    changeIsLocal = true;
                    updateRGBColor();
                    changeIsLocal = false;
                }
            }
        };

        private final DoubleProperty alpha = new SimpleDoubleProperty(100.0D) {
            @Override
            protected void invalidated() {
                if (!changeIsLocal) {
                    changeIsLocal = true;
                    setCustomColor(new Color(
                            getCustomColor().getRed(),
                            getCustomColor().getGreen(),
                            getCustomColor().getBlue(),
                            clamp(alpha.get() / 100.0D)));
                    changeIsLocal = false;
                }
            }
        };

        private void updateRGBColor() {
            var newColor = Color.rgb(red.get(), green.get(), blue.get(), clamp(alpha.get() / 100.0D));
            hue.set(newColor.getHue());
            sat.set(newColor.getSaturation() * 100.0D);
            bright.set(newColor.getBrightness() * 100.0D);
            setCustomColor(newColor);
        }

        private void updateHSBColor() {
            var newColor = Color.hsb(hue.get(), clamp(sat.get() / 100.0D),
                    clamp(bright.get() / 100.0D), clamp(alpha.get() / 100.0D));
            red.set(doubleToInt(newColor.getRed()));
            green.set(doubleToInt(newColor.getGreen()));
            blue.set(doubleToInt(newColor.getBlue()));
            setCustomColor(newColor);
        }

        private void colorChanged() {
            if (!changeIsLocal) {
                changeIsLocal = true;
                hue.set(getCustomColor().getHue());
                sat.set(getCustomColor().getSaturation() * 100.0D);
                bright.set(getCustomColor().getBrightness() * 100.0D);
                red.set(doubleToInt(getCustomColor().getRed()));
                green.set(doubleToInt(getCustomColor().getGreen()));
                blue.set(doubleToInt(getCustomColor().getBlue()));
                changeIsLocal = false;
            }
        }

        public ColorRectPane() {
            getStyleClass().add("color-rect-pane");
            customColorProperty().addListener((ov, t, t1) -> colorChanged());
            colorRectIndicator = new Region();
            colorRectIndicator.setId("color-rect-indicator");
            colorRectIndicator.setManaged(false);
            colorRectIndicator.setMouseTransparent(true);
            colorRectIndicator.setCache(true);
            var stackPane = new StackPane();
            colorRect = new StackPane() {
                @Override
                public Orientation getContentBias() {
                    return Orientation.VERTICAL;
                }

                @Override
                protected double computePrefWidth(double height) {
                    return height;
                }

                @Override
                protected double computeMaxWidth(double height) {
                    return height;
                }
            };
            colorRect.getStyleClass().addAll("color-rect", "transparent-pattern");
            var colorRectHue = new Pane();
            colorRectHue.backgroundProperty().bind(new BindingObjectBinding<>(hue) {
                @Override
                protected Background computeValue() {
                    return new Background(new BackgroundFill(
                            Color.hsb(hue.getValue(), 1.0D, 1.0D),
                            CornerRadii.EMPTY, Insets.EMPTY));
                }
            });
            var colorRectOverlayOne = new Pane();
            colorRectOverlayOne.getStyleClass().add("color-rect");
            colorRectOverlayOne.setBackground(new Background(new BackgroundFill(
                    new LinearGradient(0.0D, 0.0D, 1.0D, 0.0D, true, CycleMethod.NO_CYCLE, new Stop(0.0D, Color.rgb(255, 255, 255, 1.0D)),
                            new Stop(1.0D, Color.rgb(255, 255, 255, 0.0D))), CornerRadii.EMPTY, Insets.EMPTY)));
            EventHandler<MouseEvent> rectMouseHandler = event -> {
                var x = event.getX();
                var y = event.getY();
                sat.set(clamp(x / colorRect.getWidth()) * 100.0D);
                bright.set(100.0D - clamp(y / colorRect.getHeight()) * 100.0D);
            };
            var colorRectOverlayTwo = new Pane();
            colorRectOverlayTwo.getStyleClass().addAll("color-rect");
            colorRectOverlayTwo.setBackground(new Background(new BackgroundFill(
                    new LinearGradient(0.0D, 0.0D, 0.0D, 1.0D, true, CycleMethod.NO_CYCLE, new Stop(0.0D, Color.rgb(0, 0, 0, 0.0D)), new Stop(1.0D, Color.rgb(0, 0, 0, 1.0D))),
                    CornerRadii.EMPTY, Insets.EMPTY)));
            colorRectOverlayTwo.setOnMouseDragged(rectMouseHandler);
            colorRectOverlayTwo.setOnMousePressed(rectMouseHandler);
            var colorRectBlackBorder = new Pane();
            colorRectBlackBorder.setMouseTransparent(true);
            colorRectBlackBorder.getStyleClass().addAll("color-rect", "color-rect-border");
            colorBar = new Pane();
            colorBar.getStyleClass().add("color-bar");
            colorBar.setBackground(new Background(new BackgroundFill(createHueGradient(), CornerRadii.EMPTY, Insets.EMPTY)));
            var colorBarIndicator = new Region();
            colorBarIndicator.setId("color-bar-indicator");
            colorBarIndicator.setMouseTransparent(true);
            colorBarIndicator.setCache(true);
            colorRectIndicator.layoutXProperty().bind(sat.divide(100).multiply(colorRect.widthProperty()));
            colorRectIndicator.layoutYProperty()
                              .bind(Bindings.subtract(1, bright.divide(100)).multiply(colorRect.heightProperty()));
            colorBarIndicator.layoutYProperty().bind(hue.divide(360).multiply(colorBar.heightProperty()));
            stackPane.opacityProperty().bind(alpha.divide(100));
            EventHandler<MouseEvent> barMouseHandler = event -> {
                var y = event.getY();
                hue.set(clamp(y / colorRect.getHeight()) * 360.0D);
            };
            colorBar.setOnMouseDragged(barMouseHandler);
            colorBar.setOnMousePressed(barMouseHandler);
            colorBar.getChildren().setAll(colorBarIndicator);
            stackPane.getChildren().setAll(colorRectHue, colorRectOverlayOne, colorRectOverlayTwo);
            colorRect.getChildren().setAll(stackPane, colorRectBlackBorder, colorRectIndicator);
            HBox.setHgrow(colorRect, Priority.SOMETIMES);
            getChildren().addAll(colorRect, colorBar);
        }

        @Override
        protected void layoutChildren() {
            super.layoutChildren();
            colorRectIndicator.autosize();
            var size = Math.min(colorRect.getWidth(), colorRect.getHeight());
            colorRect.resize(size, size);
            colorBar.resize(colorBar.getWidth(), size);
        }
    }

    private class ControlsPane extends VBox {

        private final ToggleButton hsbButton;

        private final ToggleButton rgbButton;

        private final Label[] labels = new Label[4];

        private final Slider[] sliders = new Slider[4];

        private final IntegerField[] fields = new IntegerField[4];

        private final Property<Number>[] bindedProperties;

        private void showHSBSettings() {
            set(0, getString("hue_colon"), 360, colorRectPane.hue);
            set(1, getString("saturation_colon"), 100, colorRectPane.sat);
            set(2, getString("brightness_colon"), 100, colorRectPane.bright);
        }

        private void showRGBSettings() {
            set(0, getString("red_colon"), 255, colorRectPane.red);
            set(1, getString("green_colon"), 255, colorRectPane.green);
            set(2, getString("blue_colon"), 255, colorRectPane.blue);
        }

        private void showWebSettings() {
            labels[0].setText(getString("web_colon"));
        }

        public ControlsPane() {
            bindedProperties = new Property[4];
            getStyleClass().add("controls-pane");
            var newColorRect = new Region();
            newColorRect.getStyleClass().add("color-rect");
            newColorRect.setId("new-color");
            newColorRect.backgroundProperty().bind(new BindingObjectBinding<>(customColorProperty) {
                @Override
                protected Background computeValue() {
                    return new Background(
                            new BackgroundFill(customColorProperty.get(), CornerRadii.EMPTY, Insets.EMPTY));
                }
            });
            var newColorLabel = new Label("Color");
            var whiteBox = new Region();
            whiteBox.getStyleClass().add("customcolor-controls-background");
            hsbButton = new ToggleButton(getString("colorType.hsb"));
            hsbButton.getStyleClass().add("left-pill");
            rgbButton = new ToggleButton(getString("colorType.rgb"));
            rgbButton.getStyleClass().add("center-pill");
            var webButton = new ToggleButton(getString("colorType.web"));
            webButton.getStyleClass().add("right-pill");
            var group = new ToggleGroup();
            var hBox = new HBox();
            hBox.setAlignment(Pos.CENTER);
            hBox.getChildren().addAll(hsbButton, rgbButton, webButton);
            var leftSpacer = new Region();
            leftSpacer.setId("spacer-side");
            var rightSpacer = new Region();
            rightSpacer.setId("spacer-side");
            var bottomSpacer = new Region();
            bottomSpacer.setId("spacer-bottom");
            var currentAndNewColor = new GridPane();
            currentAndNewColor.getColumnConstraints().addAll(new ColumnConstraints());
            currentAndNewColor.getColumnConstraints().get(0).setHgrow(Priority.ALWAYS);
            currentAndNewColor.getRowConstraints().addAll(new RowConstraints(), new RowConstraints(), new RowConstraints());
            currentAndNewColor.getRowConstraints().get(2).setVgrow(Priority.ALWAYS);
            var labelCenterer = new HBox(newColorLabel);
            labelCenterer.setAlignment(Pos.CENTER);
            currentAndNewColor.getStyleClass().add("current-new-color-grid");
            currentAndNewColor.add(labelCenterer, 0, 0);
            currentAndNewColor.add(newColorRect, 0, 2);
            currentAndNewColor.setPrefHeight(80.0D);
            currentAndNewColor.setMaxHeight(80.0D);
            var settingsPane = new GridPane();
            settingsPane.setMaxHeight(Double.MAX_VALUE);
            VBox.setVgrow(settingsPane, Priority.ALWAYS);
            settingsPane.setId("settings-pane");
            settingsPane.getColumnConstraints()
                        .addAll(new ColumnConstraints(), new ColumnConstraints(), new ColumnConstraints(), new ColumnConstraints(), new ColumnConstraints(),
                                new ColumnConstraints());
            settingsPane.getColumnConstraints().get(0).setHgrow(Priority.NEVER);
            settingsPane.getColumnConstraints().get(2).setHgrow(Priority.ALWAYS);
            settingsPane.getColumnConstraints().get(3).setHgrow(Priority.NEVER);
            settingsPane.getColumnConstraints().get(4).setHgrow(Priority.NEVER);
            settingsPane.getColumnConstraints().get(5).setHgrow(Priority.NEVER);
            settingsPane.add(whiteBox, 0, 0, 6, 5);
            settingsPane.add(hBox, 0, 0, 6, 1);
            settingsPane.add(leftSpacer, 0, 0);
            settingsPane.add(rightSpacer, 5, 0);
            settingsPane.add(bottomSpacer, 0, 4);
            var webField = new WebColorField();
            webField.getStyleClass().add("web-field");
            webField.setSkin(new WebColorFieldSkin(webField));
            webField.valueProperty().bindBidirectional(customColorProperty);
            webField.visibleProperty().bind(group.selectedToggleProperty().isEqualTo(webButton));
            settingsPane.add(webField, 2, 1);
            for (var i = 0; i < 4; i++) {
                labels[i] = new Label();
                labels[i].getStyleClass().add("settings-label");
                sliders[i] = new Slider();
                fields[i] = new IntegerField();
                fields[i].getStyleClass().add("color-input-field");
                fields[i].setSkin(new IntegerFieldSkin(fields[i]));
                var units = new Label[4];
                units[i] = new Label((i == 0) ? "°" : "%");
                units[i].getStyleClass().add("settings-unit");
                if (i > 0 && i < 3)
                    labels[i].visibleProperty().bind(group.selectedToggleProperty().isNotEqualTo(webButton));
                if (i < 3) {
                    sliders[i].visibleProperty().bind(group.selectedToggleProperty().isNotEqualTo(webButton));
                    fields[i].visibleProperty().bind(group.selectedToggleProperty().isNotEqualTo(webButton));
                    units[i].visibleProperty().bind(group.selectedToggleProperty().isEqualTo(hsbButton));
                }
                var row = 1 + i;
                if (i == 3)
                    row++;
                if (i != 3) {
                    settingsPane.add(labels[i], 1, row);
                    settingsPane.add(sliders[i], 2, row);
                    settingsPane.add(fields[i], 3, row);
                    settingsPane.add(units[i], 4, row);
                }
            }
            set(3, getString("opacity_colon"), 100, colorRectPane.alpha);
            hsbButton.setToggleGroup(group);
            rgbButton.setToggleGroup(group);
            webButton.setToggleGroup(group);
            group.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue == null) {
                    group.selectToggle(oldValue);
                } else if (Objects.equals(newValue, hsbButton)) {
                    showHSBSettings();
                } else if (Objects.equals(newValue, rgbButton)) {
                    showRGBSettings();
                } else {
                    showWebSettings();
                }
            });
            group.selectToggle(hsbButton);
            var spacer = new VBox();
            VBox.setVgrow(spacer, Priority.ALWAYS);
            getChildren().addAll(currentAndNewColor, spacer, settingsPane);
        }

        private void set(int row, String caption, int maxValue, Property<Number> prop) {
            labels[row].setText(caption);
            if (bindedProperties[row] != null) {
                sliders[row].valueProperty().unbindBidirectional(bindedProperties[row]);
                fields[row].valueProperty().unbindBidirectional(bindedProperties[row]);
            }
            sliders[row].setMax(maxValue);
            sliders[row].valueProperty().bindBidirectional(prop);
            labels[row].setLabelFor(sliders[row]);
            fields[row].setMaxValue(maxValue);
            fields[row].valueProperty().bindBidirectional(prop);
            bindedProperties[row] = prop;
        }
    }

    static double clamp(double value) {
        return (value < 0.0D) ? 0.0D : Math.min(value, 1.0D);
    }

    private static LinearGradient createHueGradient() {
        var stops = new Stop[255];
        for (var y = 0; y < 255; y++) {
            var offset = 1.0D - 0.00392156862745098D * y;
            var h = (int) (y / 255.0D * 360.0D);
            stops[y] = new Stop(offset, Color.hsb(h, 1.0D, 1.0D));
        }
        return new LinearGradient(0.0D, 1.0D, 0.0D, 0.0D, true, CycleMethod.NO_CYCLE, stops);
    }

    private static int doubleToInt(double value) {
        return (int) (value * 255.0D + 0.5D);
    }

    private abstract static class BindingObjectBinding<T> extends ObjectBinding<T> {
        protected BindingObjectBinding(Observable... obs) {
            bind(obs);
        }
    }
}
