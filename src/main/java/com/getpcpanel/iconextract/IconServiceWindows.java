package com.getpcpanel.iconextract;

import java.awt.image.BufferedImage;
import java.io.File;

import com.getpcpanel.platform.WindowsBuild;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@WindowsBuild
public class IconServiceWindows implements IIconService {
    @Override
    public BufferedImage getIconForFile(int width, int height, File file) {
        return JIconExtract.getIconForFile(width, height, file);
    }
}

