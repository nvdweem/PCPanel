package com.getpcpanel.profile.compat;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.command.Command;

/**
 * Fills an object graph with deterministic, non-default values so the JSON it serializes to names
 * every persisted property. Used to build the current version's entry in the legacy-save fixture
 * chain (see {@code src/test/resources/legacy-saves/README.md}); driven by reflection so a property
 * added to {@code Save}/{@code Profile}/a command is covered without anyone remembering to add it.
 *
 * <p>Values come from a counter, so two runs produce byte-identical output and regenerating a fixture
 * yields a reviewable diff rather than noise.
 */
final class SavePopulator {
    /** {@code SimpleClassName#field} entries the caller builds by hand instead. */
    private final Set<String> skip;
    private final Set<Class<?>> inProgress = new HashSet<>();
    private int counter;

    SavePopulator(Set<String> skip) {
        this.skip = skip;
    }

    /** Fills an already-constructed bean's fields in place. */
    void fill(Object bean) {
        fillFields(bean, 0);
    }

    /**
     * Fills only fields that still hold the value a freshly constructed instance would have, so
     * values loaded from an older save survive while properties that version never wrote get one.
     */
    void topUp(Object bean) {
        Object reference;
        try {
            var ctor = bean.getClass().getDeclaredConstructor();
            ctor.setAccessible(true);
            reference = ctor.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("No no-arg constructor to derive defaults from: " + bean.getClass(), e);
        }
        forEachSettableField(bean.getClass(), (clazz, field) -> {
            try {
                if (!Objects.deepEquals(field.get(bean), field.get(reference))) {
                    return; // the loaded file supplied a value; keep it
                }
                var generated = value(field.getGenericType(), field.getName(), 1);
                if (generated != null) {
                    field.set(bean, generated);
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot top up " + clazz.getSimpleName() + "#" + field.getName(), e);
            }
        });
    }

    /** Builds a project type through its creator/canonical/no-arg constructor and fills what remains. */
    Object construct(Class<?> clazz, int depth) {
        if (!inProgress.add(clazz)) {
            return null; // self-referential graph (Save -> DeviceSave -> Save); stop here
        }
        try {
            var creator = pickConstructor(clazz);
            creator.setAccessible(true);
            var params = creator.getGenericParameterTypes();
            var names = creator.getParameters();
            var args = new Object[params.length];
            for (var i = 0; i < params.length; i++) {
                args[i] = value(params[i], names[i].getName(), depth + 1);
            }
            var instance = creator.newInstance(args);
            if (params.length == 0) {
                fillFields(instance, depth);
            }
            return instance;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot construct " + clazz.getName(), e);
        } finally {
            inProgress.remove(clazz); // the guard covers the current path only, not the whole run
        }
    }

    private Object value(Type type, String hint, int depth) {
        if (type instanceof ParameterizedType pt) {
            var raw = (Class<?>) pt.getRawType();
            // An element type this populator has no value for makes the whole collection unsupported:
            // a map of nulls is worse than leaving the field at its default.
            if (Map.class.isAssignableFrom(raw)) {
                var map = new LinkedHashMap<>();
                for (var i = 0; i < 2; i++) {
                    var key = value(pt.getActualTypeArguments()[0], hint + "Key", depth + 1);
                    var entry = value(pt.getActualTypeArguments()[1], hint + "Value", depth + 1);
                    if (key == null || entry == null) {
                        return null;
                    }
                    map.put(key, entry);
                }
                return map;
            }
            if (Set.class.isAssignableFrom(raw)) {
                var set = new LinkedHashSet<>();
                for (var i = 0; i < 2; i++) {
                    var element = value(pt.getActualTypeArguments()[0], hint, depth + 1);
                    if (element == null) {
                        return null;
                    }
                    set.add(element);
                }
                return set;
            }
            if (Iterable.class.isAssignableFrom(raw)) {
                var list = new ArrayList<>();
                for (var i = 0; i < 2; i++) {
                    var element = value(pt.getActualTypeArguments()[0], hint, depth + 1);
                    if (element == null) {
                        return null;
                    }
                    list.add(element);
                }
                return list;
            }
            return value(raw, hint, depth);
        }
        if (!(type instanceof Class<?> clazz)) {
            return null;
        }

        if (clazz == boolean.class || clazz == Boolean.class) return true;
        if (clazz == byte.class || clazz == Byte.class) return (byte) (next() % 120 + 1);
        if (clazz == short.class || clazz == Short.class) return (short) (next() % 1000 + 1);
        if (clazz == int.class || clazz == Integer.class) return next();
        if (clazz == long.class || clazz == Long.class) return (long) next() * 10;
        if (clazz == float.class || clazz == Float.class) return next() + 0.5f;
        if (clazz == double.class || clazz == Double.class) return next() + 0.25d;
        if (clazz == char.class || clazz == Character.class) return (char) ('a' + next() % 26);
        if (clazz == String.class) return hint + "-" + next();
        if (clazz == File.class) return new File("C:\\PCPanelCompat\\" + hint + next() + ".exe");
        if (clazz.isEnum()) {
            var constants = clazz.getEnumConstants();
            return constants[next() % constants.length];
        }
        if (clazz.isArray()) {
            var component = clazz.getComponentType();
            var array = Array.newInstance(component, 2);
            for (var i = 0; i < 2; i++) {
                Array.set(array, i, value(component, hint, depth + 1));
            }
            return array;
        }
        if (List.class.isAssignableFrom(clazz)) return new ArrayList<>(List.of(hint + "-" + next(), hint + "-" + next()));
        if (Map.class.isAssignableFrom(clazz)) return new LinkedHashMap<>();
        if (Command.class.isAssignableFrom(clazz) || clazz == Commands.class) {
            return null; // control assignments are laid out by the generator, one command type per slot
        }
        if (!clazz.getName().startsWith("com.getpcpanel") || depth > 6 || Modifier.isAbstract(clazz.getModifiers()) || clazz.isInterface()) {
            return null;
        }
        return construct(clazz, depth);
    }

    private static Constructor<?> pickConstructor(Class<?> clazz) {
        var ctors = clazz.getDeclaredConstructors();
        for (var c : ctors) {
            if (c.isAnnotationPresent(JsonCreator.class)) {
                return c;
            }
        }
        if (clazz.isRecord()) {
            var componentTypes = Stream.of(clazz.getRecordComponents()).map(rc -> rc.getType()).toArray(Class<?>[]::new);
            try {
                return clazz.getDeclaredConstructor(componentTypes);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(e);
            }
        }
        for (var c : ctors) {
            if (c.getParameterCount() == 0) {
                return c;
            }
        }
        return Stream.of(ctors).max(Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();
    }

    private void fillFields(Object bean, int depth) {
        forEachSettableField(bean.getClass(), (clazz, field) -> {
            var generated = value(field.getGenericType(), field.getName(), depth + 1);
            if (generated == null) {
                return; // unsupported type: leave the constructor's own value in place
            }
            try {
                field.set(bean, generated);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot set " + clazz.getSimpleName() + "#" + field.getName(), e);
            }
        });
    }

    /** Walks the class hierarchy in a stable order, handing over every field this populator may write. */
    private void forEachSettableField(Class<?> type, java.util.function.BiConsumer<Class<?>, Field> action) {
        for (var clazz = type; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            var fields = clazz.getDeclaredFields().clone();
            Arrays.sort(fields, Comparator.comparing(Field::getName)); // declaration order is unspecified; keep runs identical
            for (var field : fields) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())
                        || field.isSynthetic() || skip.contains(clazz.getSimpleName() + "#" + field.getName())) {
                    continue;
                }
                field.setAccessible(true);
                action.accept(clazz, field);
            }
        }
    }

    private int next() {
        return ++counter;
    }
}
