package com.getpcpanel.integration.volume.overlay;

/**
 * The size of the primary screen, used to position the overlay. A plain value type so the overlay
 * abstraction does not depend on {@code java.awt.Dimension} (whose class initializer touches the AWT
 * graphics environment and is unavailable in the libawt-less native images on macOS/Linux).
 */
public record ScreenSize(int width, int height) {
}
