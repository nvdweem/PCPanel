package com.getpcpanel.util.tray.wayland;

import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.Position;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * A single icon bitmap as defined by the StatusNotifierItem {@code IconPixmap} property
 * ({@code a(iiay)}). The pixel data is 32-bit ARGB stored in network (big-endian) byte order.
 *
 * <p>dbus-java marshals a {@link Struct} by reading its {@link Position}-annotated fields
 * reflectively, so the fields must be registered for reflection; without it the struct serialises
 * empty in a native image and the malformed reply makes the D-Bus daemon drop the connection.
 *
 * @see <a href="https://www.freedesktop.org/wiki/Specifications/StatusNotifierItem/">StatusNotifierItem spec</a>
 */
@RegisterForReflection
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
