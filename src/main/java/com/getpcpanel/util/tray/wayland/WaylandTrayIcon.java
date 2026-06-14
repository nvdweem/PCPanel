package com.getpcpanel.util.tray.wayland;

import java.util.List;

import lombok.extern.log4j.Log4j2;

/**
 * Supplies the PCPanel tray icon as the ARGB pixmap data expected by the StatusNotifierItem
 * {@code IconPixmap} property.
 *
 * <p>The previous implementation decoded {@code /assets/32x32.png} with {@code javax.imageio.ImageIO},
 * which needs libawt. Since the Linux native image no longer bundles AWT, image decoding is gone and
 * the tray currently exposes no pixmap (the SNI host falls back to its default icon). A sensible
 * AWT-free replacement is to ship a pre-decoded raw-ARGB resource and read its bytes directly here.
 */
@Log4j2
final class WaylandTrayIcon {
    private WaylandTrayIcon() {
    }

    /** Returns the tray pixmap, or an empty list when no AWT-free icon source is available. */
    static List<IconPixmap> pixmap() {
        return List.of();
    }
}
