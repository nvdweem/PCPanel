package com.getpcpanel.template;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

/**
 * A read-only map whose values are looked up only when a template asks for a key, so a namespace can expose
 * every channel/device/user without building a view for each one on every render. Qute resolves
 * {@code {{ map.key }}} and {@code {{ map.get('key') }}} through {@link #get}; iterating ({@code #for}) builds
 * the entries.
 */
public final class LazyMap<V> extends AbstractMap<String, V> {
    private final Supplier<? extends Collection<String>> keys;
    private final Function<String, V> lookup;

    public LazyMap(Supplier<? extends Collection<String>> keys, Function<String, V> lookup) {
        this.keys = keys;
        this.lookup = lookup;
    }

    public static <V> LazyMap<V> of(Map<String, ?> source, Function<String, V> lookup) {
        return new LazyMap<>(source::keySet, lookup);
    }

    @Override
    public boolean containsKey(Object key) {
        return key instanceof String s && keys.get().contains(s);
    }

    @Override
    @Nullable
    public V get(Object key) {
        return containsKey(key) ? lookup.apply((String) key) : null;
    }

    @Override
    public Set<String> keySet() {
        return new LinkedHashSet<>(keys.get());
    }

    @Override
    public Set<Entry<String, V>> entrySet() {
        var entries = new LinkedHashSet<Entry<String, V>>();
        for (var key : keys.get()) {
            entries.add(new SimpleImmutableEntry<>(key, lookup.apply(key)));
        }
        return entries;
    }
}
