package com.getpcpanel.iconextract;

import java.awt.image.BufferedImage;
import java.io.File;

import javax.annotation.Nullable;

import com.getpcpanel.util.FxImageUtil;

import io.quarkus.cache.CacheResult;
import javafx.scene.image.Image;

public interface IIconService {
    @CacheResult(cacheName = "icon")
    BufferedImage getIconForFile(int width, int height, File file);

    @Nullable
    @CacheResult(cacheName = "icon")
    default Image getIconImageForFile(int width, int height, File file) {
        var image = getIconForFile(width, height, file);
        if (image != null) {
            return FxImageUtil.toFxImage(image);
        }
        return null;
    }
}
