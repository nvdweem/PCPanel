package com.getpcpanel.iconextract;

import java.awt.image.BufferedImage;
import java.io.File;

import com.getpcpanel.platform.LinuxBuild;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@LinuxBuild
public class IconServiceLinux implements IIconService {
    @Override
    public BufferedImage getIconForFile(int width, int height, File file) {
        return null;
    }
}

