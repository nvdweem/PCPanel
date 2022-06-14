package com.getpcpanel.ui;

import javafx.scene.layout.Pane;

public interface UIInitializer {
    default <T> void initUI(Pane pane, T... args) {
    }

    default <T, R> R getUIArg(Class<R> expected, T[] args, int idx) {
        return getUIArg(expected, args, idx, null);
    }

    default <T, R> R getUIArg(Class<R> expected, T[] args, int idx, R def) {
        if (idx >= args.length) {
            throw new IllegalArgumentException("%s is out of bounds (%s)".formatted(idx, args.length));
        }
        var result = args[idx];
        if (result == null) {
            return def;
        }
        if (expected.isInstance(result)) {
            //noinspection unchecked
            return (R) result;
        }
        throw new IllegalArgumentException("Argument %s was expected to be %s but was %s".formatted(idx, expected, result));
    }
}
