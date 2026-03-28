package com.getpcpanel.iconextract;

import java.awt.image.BufferedImage;
import java.io.File;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;

@ApplicationScoped
@Typed(IconServiceLinux.class)
public class IconServiceLinux implements IIconService {
    @Override
    public BufferedImage getIconForFile(int width, int height, File file) {
        return null;
    }
}
