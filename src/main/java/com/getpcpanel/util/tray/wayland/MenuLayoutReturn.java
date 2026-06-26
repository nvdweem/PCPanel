package com.getpcpanel.util.tray.wayland;

import org.freedesktop.dbus.Tuple;
import org.freedesktop.dbus.annotations.Position;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Return value of {@code com.canonical.dbusmenu.GetLayout}: the two out-arguments
 * {@code (u revision, (ia{sv}av) layout)}. dbus-java represents a multi-value return as a
 * {@link Tuple}, and marshals it by reading the <em>generic</em> type arguments of the declared return
 * type — so the class must be parameterized (see the {@link Tuple} javadoc's {@code MyTuple<A,B>}
 * example); a concrete, non-generic tuple is rejected as "non-exportable" at runtime.
 */
@RegisterForReflection
public class MenuLayoutReturn<A, B> extends Tuple {
    @Position(0)
    private final A revision;
    @Position(1)
    private final B layout;

    public MenuLayoutReturn(A revision, B layout) {
        this.revision = revision;
        this.layout = layout;
    }

    public A getRevision() {
        return revision;
    }

    public B getLayout() {
        return layout;
    }
}
