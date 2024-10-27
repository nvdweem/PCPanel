package com.getpcpanel.ui.graphviewer;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.hid.DialValueCalculator;
import com.getpcpanel.util.Util;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.paint.Color;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import lombok.Setter;

@Setter
public class GraphViewer extends Button {
    private static final int DATA_POINTS = 255;

    private final Path path = new Path();
    private DialValueCalculator calculator;
    private Command cmd;

    public GraphViewer(DialValueCalculator calculator, Command cmd) {
        this.calculator = calculator;
        this.cmd = cmd;

        setStyle("""
                -fx-background-color: transparent;
                -fx-border-color: #232428;
                -fx-border-width: 1px;
                """);

        path.setStroke(Color.SILVER);
        setGraphic(path);
        setPadding(Insets.EMPTY);
        redraw();
    }

    @Override
    public void setPrefSize(double prefWidth, double prefHeight) {
        super.setPrefSize(prefWidth, prefHeight);
        redraw();
    }

    public void redraw() {
        path.getElements().clear();

        for (var i = 0; i < DATA_POINTS; i++) {
            var x = Util.map(i, 0, DATA_POINTS, 0, getPrefWidth());
            var y = 100 - Util.map(calculator.calcValue(cmd, i, 0F, 100F), 0, 100, 0, getPrefHeight());

            if (i == 0) {
                path.getElements().add(new MoveTo(x,y));
            } else {
                path.getElements().add(new LineTo(x,y));
            }
        }
    }
}
