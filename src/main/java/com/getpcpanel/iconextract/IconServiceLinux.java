package com.getpcpanel.iconextract;

import java.awt.image.BufferedImage;
import java.io.File;

import org.springframework.stereotype.Service;

import com.getpcpanel.spring.ConditionalOnLinux;

@Service
@ConditionalOnLinux
public class IconServiceLinux implements IIconService {
    @Override
    public BufferedImage getIconForFile(int width, int height, File file) {
        return null;
    }
}
