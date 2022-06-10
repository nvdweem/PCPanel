package com.getpcpanel.iconextract;

import java.awt.image.BufferedImage;
import java.io.File;

import org.springframework.stereotype.Service;

import com.getpcpanel.spring.ConditionalOnWindows;

@Service
@ConditionalOnWindows
public class IconServiceWindows implements IIconService {
    @Override
    public BufferedImage getIconForFile(int width, int height, File file) {
        return JIconExtract.getIconForFile(width, height, file);
    }
}
