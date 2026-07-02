package com.getpcpanel.integration.testutil;

import java.lang.annotation.Annotation;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

/**
 * Minimal {@link CDI} provider for plain unit tests: commands resolve their collaborators through
 * {@code CdiHelper.getBean} → {@code CDI.current()}, so registering a hand-written stub here lets a
 * command's real {@code execute} path run without a container. Only {@code select(Class)} +
 * {@code get()} are implemented — exactly what {@code CdiHelper} uses.
 *
 * <p>The provider installs globally for the JVM (there is no unset API), which is harmless: nothing
 * else in the test JVM calls {@code CDI.current()}. Tests should still {@link #clear()} their beans
 * afterwards so stubs cannot leak between test classes.
 */
public final class FakeCdi extends CDI<Object> {
    private static final FakeCdi CURRENT = new FakeCdi();
    private static final Map<Class<?>, Object> beans = new ConcurrentHashMap<>();

    private FakeCdi() {
    }

    /** Installs the fake provider (idempotent) and serves {@code bean} for lookups of {@code type}. */
    public static <T> void register(Class<T> type, T bean) {
        CDI.setCDIProvider(() -> CURRENT);
        beans.put(type, bean);
    }

    public static void clear() {
        beans.clear();
    }

    @Override
    public <U> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
        return new StubInstance<>(subtype);
    }

    @Override
    public BeanManager getBeanManager() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Instance<Object> select(Annotation... qualifiers) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <U> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isUnsatisfied() {
        return false;
    }

    @Override
    public boolean isAmbiguous() {
        return false;
    }

    @Override
    public void destroy(Object instance) {
    }

    @Override
    public Handle<Object> getHandle() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterable<? extends Handle<Object>> handles() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object get() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<Object> iterator() {
        throw new UnsupportedOperationException();
    }

    private record StubInstance<U>(Class<U> type) implements Instance<U> {
        @Override
        public U get() {
            var bean = beans.get(type);
            if (bean == null) {
                throw new IllegalStateException("No fake bean registered for " + type.getName());
            }
            return type.cast(bean);
        }

        @Override
        public boolean isUnsatisfied() {
            return !beans.containsKey(type);
        }

        @Override
        public boolean isAmbiguous() {
            return false;
        }

        @Override
        public Instance<U> select(Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <V extends U> Instance<V> select(Class<V> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <V extends U> Instance<V> select(TypeLiteral<V> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void destroy(U instance) {
        }

        @Override
        public Handle<U> getHandle() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterable<? extends Handle<U>> handles() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterator<U> iterator() {
            throw new UnsupportedOperationException();
        }
    }
}
