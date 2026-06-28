package com.getpcpanel.integration.program.iconextract;

import java.awt.image.BufferedImage;
import java.io.File;

import jakarta.annotation.Nullable;

public interface IIconService {
    @Nullable
    BufferedImage getIconForFile(int width, int height, File file);
}
