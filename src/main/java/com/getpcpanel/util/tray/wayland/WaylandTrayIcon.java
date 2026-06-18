package com.getpcpanel.util.tray.wayland;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.log4j.Log4j2;

/**
 * Supplies the PCPanel tray icon as the ARGB pixmap data expected by the StatusNotifierItem
 * {@code IconPixmap} property.
 *
 * <p>Decoding a PNG at runtime would need {@code javax.imageio.ImageIO} (libawt), which the Linux
 * native image no longer bundles. Instead the build ships a pre-decoded raw-ARGB resource at
 * {@code /assets/tray-icon.argb} - the same artwork as the web favicon - and this class reads its
 * bytes directly, so no AWT is involved. Without a pixmap the SNI host falls back to a blank
 * default icon.
 *
 * <p>Resource format (all integers big-endian, matching the {@code IconPixmap} {@code a(iiay)}
 * ARGB-in-network-byte-order layout): {@code int count}, then for each frame {@code int width},
 * {@code int height}, {@code width*height*4} bytes of ARGB. Multiple sizes (16x16, 32x32) let the
 * host pick the best for the panel.
 */
@Log4j2
final class WaylandTrayIcon {
    private static final String ICON_RESOURCE = "/assets/tray-icon.argb";

    /**
     * Loaded lazily on first use rather than in a static initializer: {@link IconPixmap} extends the
     * D-Bus {@code Struct} type, which is {@code --initialize-at-run-time} in the native image, so
     * building these instances eagerly would pin run-time-only objects into the build-time image heap
     * and abort the native build (UnsupportedFeatureException). Deferring keeps construction at runtime.
     */
    private static volatile List<IconPixmap> pixmap;

    private WaylandTrayIcon() {
    }

    /** Returns the tray pixmap, or an empty list when no AWT-free icon source is available. */
    static List<IconPixmap> pixmap() {
        var result = pixmap;
        if (result == null) {
            synchronized (WaylandTrayIcon.class) {
                result = pixmap;
                if (result == null) {
                    result = pixmap = load();
                }
            }
        }
        return result;
    }

    private static List<IconPixmap> load() {
        try (InputStream is = WaylandTrayIcon.class.getResourceAsStream(ICON_RESOURCE)) {
            if (is == null) {
                log.warn("Tray icon resource {} not found; tray will use the host's fallback icon", ICON_RESOURCE);
                return List.of();
            }
            var in = new DataInputStream(is);
            var count = in.readInt();
            var frames = new ArrayList<IconPixmap>(count);
            for (var i = 0; i < count; i++) {
                var width = in.readInt();
                var height = in.readInt();
                var data = new byte[Math.multiplyExact(Math.multiplyExact(width, height), 4)];
                in.readFully(data);
                frames.add(new IconPixmap(width, height, data));
            }
            return List.copyOf(frames);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not load tray icon {}: {}", ICON_RESOURCE, e.toString());
            return List.of();
        }
    }
}
