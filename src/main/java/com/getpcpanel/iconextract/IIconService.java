package com.getpcpanel.iconextract;

import java.awt.image.BufferedImage;
import java.io.File;

import javax.annotation.Nullable;

import org.springframework.cache.annotation.Cacheable;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

public interface IIconService {
    @Cacheable("icon")
    BufferedImage getIconForFile(int width, int height, File file);

    @Nullable
    @Cacheable("icon")
    default Image getIconImageForFile(int width, int height, File file) {
        var image = getIconForFile(width, height, file);
        if (image != null) {
            return SwingFXUtils.toFXImage(image, null);
        }
        return null;
    }
}
