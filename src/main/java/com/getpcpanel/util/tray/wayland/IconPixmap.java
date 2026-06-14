package com.getpcpanel.util.tray.wayland;

import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.Position;

/**
 * A single icon bitmap as defined by the StatusNotifierItem {@code IconPixmap} property
 * ({@code a(iiay)}). The pixel data is 32-bit ARGB stored in network (big-endian) byte order.
 *
 * @see <a href="https://www.freedesktop.org/wiki/Specifications/StatusNotifierItem/">StatusNotifierItem spec</a>
 */
public class IconPixmap extends Struct {
    @Position(0)
    private final int width;
    @Position(1)
    private final int height;
    @Position(2)
    private final byte[] data;

    public IconPixmap(int width, int height, byte[] data) {
        this.width = width;
        this.height = height;
        this.data = data;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public byte[] getData() {
        return data;
    }
}
