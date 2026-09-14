package com.getpcpanel.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.getpcpanel.AppLikeMapper;

import io.quarkus.qute.TemplateData;

/**
 * Every type a template can reach must carry {@code @TemplateData}: Quarkus generates a reflection-free resolver
 * for it at build time. An unannotated type still renders on the JVM (dev mode, these tests) but its properties are
 * "not found" in the native image, so this walks every namespace root and the core views and fails on any gap.
 */
class TemplateDataCoverageTest {
    private static final Set<Class<?>> SCALARS = Set.of(String.class, Boolean.class, boolean.class, Integer.class, int.class, Long.class, long.class,
            Double.class, double.class, Float.class, float.class, Object.class);
    private static final Set<String> IGNORED = Set.of("toString", "hashCode", "getClass");

    static List<String> uncovered(Collection<Class<?>> roots) {
        var problems = new ArrayList<String>();
        var seen = new HashSet<Class<?>>();
        roots.forEach(root -> walk(root, root.getSimpleName(), seen, problems));
        return problems;
    }

    private static void walk(Type type, String path, Set<Class<?>> seen, List<String> problems) {
        if (type instanceof ParameterizedType p) {
            var raw = (Class<?>) p.getRawType();
            if (Map.class.isAssignableFrom(raw)) {
                walk(p.getActualTypeArguments()[p.getActualTypeArguments().length - 1], path + ".<key>", seen, problems);
                return;
            }
            if (Collection.class.isAssignableFrom(raw)) {
                walk(p.getActualTypeArguments()[0], path + ".get(n)", seen, problems);
                return;
            }
            type = raw;
        }
        if (!(type instanceof Class<?> c) || SCALARS.contains(c) || !seen.add(c)) {
            return;
        }
        if (Map.class.isAssignableFrom(c) || Collection.class.isAssignableFrom(c)) {
            problems.add(path + ": raw " + c.getSimpleName() + " — declare its element type so it can be checked");
            return;
        }
        if (!c.isAnnotationPresent(TemplateData.class)) {
            problems.add(path + ": " + c.getName() + " has no @TemplateData");
            return;
        }
        for (Method m : c.getMethods()) {
            if (m.getParameterCount() == 0 && !Modifier.isStatic(m.getModifiers()) && m.getDeclaringClass() != Object.class && !IGNORED.contains(m.getName())
                    && m.getReturnType() != void.class) {
                walk(m.getGenericReturnType(), path + "." + m.getName(), seen, problems);
            }
        }
    }

    private static List<Class<?>> templateRoots() throws Exception {
        var roots = new ArrayList<Class<?>>();
        roots.add(CoreTemplateVariables.DeviceView.class);
        roots.add(CoreTemplateVariables.ControlView.class);
        for (var type : AppLikeMapper.scanProjectClasses()) {
            if (TemplateNamespace.class.isAssignableFrom(type) && !type.isInterface() && !Modifier.isAbstract(type.getModifiers())) {
                var ctor = type.getDeclaredConstructor();
                ctor.setAccessible(true);
                roots.add(((TemplateNamespace) ctor.newInstance()).rootType());
            }
        }
        return roots;
    }

    @Test
    void everyReachableTypeHasTemplateData() throws Exception {
        var roots = templateRoots();
        assertTrue(roots.size() >= 9, "expected the core views and every integration namespace, found " + roots);
        assertEquals(List.of(), uncovered(roots));
    }

    @TemplateData
    public static final class Covered {
        public Uncovered getChild() {
            return new Uncovered();
        }
    }

    public static final class Uncovered {
        public String getName() {
            return "x";
        }
    }

    @Test
    void guardDetectsAnUnannotatedType() {
        var problems = uncovered(List.of(Covered.class));
        assertFalse(problems.isEmpty());
        assertTrue(problems.getFirst().contains("Uncovered"), problems.toString());
    }
}
