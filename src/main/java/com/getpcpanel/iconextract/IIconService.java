package com.getpcpanel.iconextract;

import java.awt.image.BufferedImage;
import java.io.File;

public interface IIconService {
    BufferedImage getIconForFile(int width, int height, File file);
}
