package com.getpcpanel;

import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.BeanContainer;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.CDIProvider;
import jakarta.enterprise.util.TypeLiteral;

/**
 * A do-nothing CDI container for tests that serialize commands outside the running app.
 *
 * <p>Several commands expose derived properties that reach into runtime services — for instance
 * {@code CommandVolumeDefaultDevice.getOverlayText()} asks the audio facade for the device's name —
 * and Jackson calls those getters while writing a {@code Save}. Without a container,
 * {@code CdiHelper.getBean} fails with "Unable to locate CDIProvider". Every stub method answers with
 * an empty value, which is what the real services return for a device or source that is not present,
 * so the JSON produced matches what the app writes when the referenced target is offline.
 */
public final class StubBeans extends CDI<Object> {
    /** Installs the stub container. Safe to call repeatedly. */
    public static void install() {
        CDI.setCDIProvider(new CDIProvider() {
            @Override
            public CDI<Object> getCDI() {
                return new StubBeans();
            }
        });
    }

    /** A proxy for {@code iface} whose every method returns an empty value of its return type. */
    @SuppressWarnings("unchecked")
    public static <T> T of(Class<T> iface) {
        if (!iface.isInterface()) {
            return null;
        }
        return (T) Proxy.newProxyInstance(StubBeans.class.getClassLoader(), new Class<?>[] { iface }, (proxy, method, args) -> switch (method.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "stub:" + iface.getSimpleName();
            default -> empty(method.getReturnType());
        });
    }

    private static Object empty(Class<?> type) {
        if (type == void.class) return null;
        if (type == boolean.class || type == Boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == char.class) return ' ';
        if (type == java.util.Map.class) return new LinkedHashMap<>();
        if (type == List.class || type == java.util.Collection.class) return new ArrayList<>();
        if (type == java.util.Set.class) return new LinkedHashSet<>();
        if (type == Optional.class) return Optional.empty();
        return of(type);
    }

    @Override
    public BeanManager getBeanManager() {
        return of(BeanManager.class);
    }

    @Override
    public BeanContainer getBeanContainer() {
        return of(BeanContainer.class);
    }

    @Override
    public <U> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
        return instanceOf(of(subtype));
    }

    @Override
    public <U> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
        return instanceOf(null);
    }

    @Override
    public Instance<Object> select(Annotation... qualifiers) {
        return instanceOf(null);
    }

    @Override
    public Object get() {
        return null;
    }

    @Override
    public Iterator<Object> iterator() {
        return List.of().iterator();
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
        return null;
    }

    @Override
    public Iterable<? extends Handle<Object>> handles() {
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static <U> Instance<U> instanceOf(U value) {
        return (Instance<U>) Proxy.newProxyInstance(StubBeans.class.getClassLoader(), new Class<?>[] { Instance.class }, (proxy, method, args) -> switch (method.getName()) {
            case "get" -> value;
            case "isResolvable", "isUnsatisfied", "isAmbiguous" -> value == null;
            case "iterator" -> (value == null ? List.of() : List.of(value)).iterator();
            case "stream" -> (value == null ? List.of() : List.of(value)).stream();
            case "handles" -> List.of();
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "stubInstance";
            default -> empty(method.getReturnType());
        });
    }
}
