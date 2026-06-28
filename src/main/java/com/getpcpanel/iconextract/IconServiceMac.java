package com.getpcpanel.iconextract;

import java.awt.image.BufferedImage;
import java.io.File;

import com.getpcpanel.platform.MacBuild;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.log4j.Log4j2;

/**
 * macOS icon service. The native image on macOS has no AWT/libawt, so application icons cannot be
 * decoded into {@link BufferedImage}s (which is the currency of the cross-platform icon pipeline).
 * Returning {@code null} makes every caller (overlay, process picker, REST {@code /api/icons}) fall
 * back to its default-icon path without touching AWT.
 *
 * <p>Extracting icons here would require a non-AWT image path (e.g. NSWorkspace/Quartz via JNA);
 * until that exists, macOS runs without per-application icons.
 */
@Log4j2
@ApplicationScoped
@MacBuild
public class IconServiceMac implements IIconService {
    @Override
    public BufferedImage getIconForFile(int width, int height, File file) {
        return null;
    }
}
