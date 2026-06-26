package com.getpcpanel.util.tray.wayland;

import java.util.List;
import java.util.Map;

import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.Position;
import org.freedesktop.dbus.types.Variant;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * One node of a {@code com.canonical.dbusmenu} layout — the recursive D-Bus type {@code (ia{sv}av)}:
 * an id, a property map ({@code label}, {@code enabled}, …) and a list of child nodes (each wrapped in
 * a {@link Variant}). dbus-java marshals a {@link Struct} reflectively via its {@link Position} fields,
 * so the class is registered for reflection.
 */
@RegisterForReflection
public class MenuItemLayout extends Struct {
    @Position(0)
    private final int id;
    @Position(1)
    private final Map<String, Variant<?>> properties;
    @Position(2)
    private final List<Variant<?>> children;

    public MenuItemLayout(int id, Map<String, Variant<?>> properties, List<Variant<?>> children) {
        this.id = id;
        this.properties = properties;
        this.children = children;
    }

    public int getId() {
        return id;
    }

    public Map<String, Variant<?>> getProperties() {
        return properties;
    }

    public List<Variant<?>> getChildren() {
        return children;
    }
}
