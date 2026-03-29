package com.getpcpanel.util;

import java.awt.image.BufferedImage;

import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

public final class FxImageUtil {
    private FxImageUtil() {
    }

    public static Image toFxImage(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] argb = new int[width * height];
        source.getRGB(0, 0, width, height, argb, 0, width);

        WritableImage target = new WritableImage(width, height);
        target.getPixelWriter().setPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), argb, 0, width);
        return target;
    }
}

