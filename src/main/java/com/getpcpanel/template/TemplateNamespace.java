package com.getpcpanel.template;

import javax.annotation.Nullable;

/**
 * An integration's variables, reachable in templates under {@link #name()} (e.g. {@code {{ wl.channel.music.level }}}).
 *
 * <p>The root is a {@code @TemplateData} view whose getters read the integration's already-held state when a
 * template reaches them. Getters must not do I/O or block: the overlay renders on the device input thread. Every
 * type reachable from {@link #rootType()} needs {@code @TemplateData} (or be a {@link LazyMap}, {@code String},
 * {@code Number} or {@code Boolean}) — {@code TemplateDataCoverageTest} enforces it, since an unregistered type
 * renders on the JVM but not in the native image.
 */
public interface TemplateNamespace {
    String name();

    /** Type of the object {@link #root} returns; used by the coverage test and the variable catalog. */
    Class<?> rootType();

    /** The root view for one render; {@code scope} lets it resolve the control's own {@code target}. */
    @Nullable
    Object root(TemplateScope scope);
}
