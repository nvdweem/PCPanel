package com.getpcpanel.iconextract;

import java.awt.image.BufferedImage;
import java.io.File;

import com.getpcpanel.spring.WindowsImpl;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@WindowsImpl
public class IconServiceWindows implements IIconService {
    @Override
    public BufferedImage getIconForFile(int width, int height, File file) {
        return JIconExtract.getIconForFile(width, height, file);
    }
}

