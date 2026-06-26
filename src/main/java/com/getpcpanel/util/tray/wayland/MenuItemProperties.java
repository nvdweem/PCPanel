package com.getpcpanel.util.tray.wayland;

import java.util.Map;

import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.Position;
import org.freedesktop.dbus.types.Variant;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * One entry returned by {@code com.canonical.dbusmenu.GetGroupProperties} — the D-Bus type
 * {@code (ia{sv})}: a menu item id and its properties.
 */
@RegisterForReflection
public class MenuItemProperties extends Struct {
    @Position(0)
    private final int id;
    @Position(1)
    private final Map<String, Variant<?>> properties;

    public MenuItemProperties(int id, Map<String, Variant<?>> properties) {
        this.id = id;
        this.properties = properties;
    }

    public int getId() {
        return id;
    }

    public Map<String, Variant<?>> getProperties() {
        return properties;
    }
}
