package com.getpcpanel.util.tray.wayland;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

import lombok.extern.log4j.Log4j2;

/**
 * Loads the PCPanel application icon from the classpath and exposes it as the ARGB pixmap data
 * expected by the StatusNotifierItem {@code IconPixmap} property.
 */
@Log4j2
final class WaylandTrayIcon {
    private static final String ICON_RESOURCE = "/assets/32x32.png";
    private static volatile List<IconPixmap> cached;

    private WaylandTrayIcon() {
    }

    /**
     * Returns the cached PCPanel tray icon, loading it lazily on first access. Loading happens at
     * runtime (when the StatusNotifierItem host first reads the property) rather than at class-init
     * time, to keep image decoding out of the native-image build.
     */
    static List<IconPixmap> pixmap() {
        var local = cached;
        if (local == null) {
            synchronized (WaylandTrayIcon.class) {
                local = cached;
                if (local == null) {
                    local = load();
                    cached = local;
                }
            }
        }
        return local;
    }

    private static List<IconPixmap> load() {
        try {
            var url = WaylandTrayIcon.class.getResource(ICON_RESOURCE);
            if (url == null) {
                log.warn("Tray icon resource {} not found", ICON_RESOURCE);
                return List.of();
            }
            var image = ImageIO.read(url);
            return List.of(toPixmap(image));
        } catch (IOException e) {
            log.warn("Failed to load tray icon {}: {}", ICON_RESOURCE, e.getMessage());
            return List.of();
        }
    }

    /**
     * Converts a {@link BufferedImage} to an {@link IconPixmap}. SNI expects 32-bit ARGB pixels in
     * network (big-endian) byte order, i.e. the bytes for each pixel are laid out A, R, G, B.
     */
    private static IconPixmap toPixmap(BufferedImage image) {
        var width = image.getWidth();
        var height = image.getHeight();
        var data = new byte[width * height * 4];
        var i = 0;
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                var argb = image.getRGB(x, y);
                data[i++] = (byte) ((argb >> 24) & 0xFF); // A
                data[i++] = (byte) ((argb >> 16) & 0xFF); // R
                data[i++] = (byte) ((argb >> 8) & 0xFF);  // G
                data[i++] = (byte) (argb & 0xFF);         // B
            }
        }
        return new IconPixmap(width, height, data);
    }
}
