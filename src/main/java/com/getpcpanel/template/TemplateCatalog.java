package com.getpcpanel.template;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.getpcpanel.template.rest.dto.TemplateVariableDto;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * The completion entries of the template editor: one level of the variable tree at a time, with current values. Walks
 * the live views the same way a template does, so what is offered is exactly what renders.
 */
@Log4j2
@ApplicationScoped
public class TemplateCatalog {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern GET_SEGMENT = Pattern.compile("get\\('(.*)'\\)|get\\((\\d+)\\)");
    private static final Set<String> IGNORED = Set.of("toString", "hashCode", "getClass");
    private static final Map<String, String> CORE_DOCS = Map.of(
            "value", "The value this field works with: the mapped value of the action, or the percentage in the overlay",
            "percent", "The control's output level, 0–100",
            "raw", "The control's hardware position, 0–255",
            "name", "What the automatic name would have been",
            "muted", "Whether what the control drives is muted",
            "device", "The device (name, serial, kind)",
            "profile", "The active profile",
            "control", "The control (label, index, kind)",
            "focusApp", "The focused application");

    @Inject
    TemplateService templates;

    public List<TemplateVariableDto> children(String path, TemplateScope scope) {
        var segments = split(path);
        if (segments.isEmpty()) {
            return roots(scope);
        }
        var target = resolve(segments, scope);
        if (target == null) {
            return List.of();
        }
        return describe(target);
    }

    private List<TemplateVariableDto> roots(TemplateScope scope) {
        var result = new ArrayList<TemplateVariableDto>();
        for (var name : CoreTemplateVariables.NAMES.stream().sorted().toList()) {
            result.add(entry(name, name, null, CORE_DOCS.get(name), root(name, scope)));
        }
        for (var namespace : templates.namespaces().stream().sorted(Comparator.comparing(TemplateNamespace::name)).toList()) {
            var doc = namespace.rootType().getAnnotation(TemplateDoc.class);
            result.add(new TemplateVariableDto(namespace.name(), namespace.name(), "object", null, doc == null ? null : doc.value(), null));
        }
        for (var fn : TemplateFunctions.MATH_DOCS) {
            result.add(new TemplateVariableDto(fn.name(), fn.insert(), "function", null, fn.description(), null));
        }
        return result;
    }

    @Nullable
    private Object resolve(List<String> segments, TemplateScope scope) {
        Object current = root(segments.getFirst(), scope);
        for (var segment : segments.subList(1, segments.size())) {
            if (current == null) {
                return null;
            }
            current = property(current, segment);
        }
        return current;
    }

    /** A root's current value; a variable that cannot be read shows without a value rather than failing the list. */
    @Nullable
    private Object root(String name, TemplateScope scope) {
        try {
            return templates.root(name, scope);
        } catch (RuntimeException e) {
            log.debug("Template catalog could not read {}", name, e);
            return null;
        }
    }

    @Nullable
    private static Object property(Object base, String segment) {
        var get = GET_SEGMENT.matcher(segment);
        if (get.matches()) {
            if (base instanceof Map<?, ?> map && get.group(1) != null) {
                return map.get(get.group(1));
            }
            if (base instanceof List<?> list && get.group(2) != null) {
                var index = Integer.parseInt(get.group(2));
                return index < list.size() ? list.get(index) : null;
            }
            return null;
        }
        if (base instanceof Map<?, ?> map) {
            return map.get(segment);
        }
        var method = getter(base.getClass(), segment);
        return method == null ? null : invoke(method, base);
    }

    private static List<TemplateVariableDto> describe(Object target) {
        var result = new ArrayList<TemplateVariableDto>();
        if (target instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                var key = String.valueOf(entry.getKey());
                var insert = IDENTIFIER.matcher(key).matches() ? key : "get('" + key.replace("'", "\\'") + "')";
                result.add(entry(key, insert, labelOf(entry.getValue()), null, entry.getValue()));
            }
            return result;
        }
        if (target instanceof List<?> list) {
            for (var i = 0; i < list.size(); i++) {
                result.add(entry(String.valueOf(i), "get(" + i + ")", labelOf(list.get(i)), null, list.get(i)));
            }
            return result;
        }
        if (target instanceof Number) {
            TemplateFunctions.NUMBER_DOCS.forEach(fn -> result.add(new TemplateVariableDto(fn.name(), fn.insert(), "function", null, fn.description(), null)));
            return result;
        }
        if (target instanceof String || target instanceof Boolean) {
            TemplateFunctions.STRING_DOCS.forEach(fn -> result.add(new TemplateVariableDto(fn.name(), fn.insert(), "function", null, fn.description(), null)));
            return result;
        }
        for (var method : properties(target.getClass())) {
            var name = propertyName(method);
            var doc = method.getAnnotation(TemplateDoc.class);
            result.add(entry(name, name, null, doc == null ? null : doc.value(), invoke(method, target)));
        }
        return result;
    }

    private static TemplateVariableDto entry(String name, String insert, @Nullable String label, @Nullable String description, @Nullable Object value) {
        return new TemplateVariableDto(name, insert, kindOf(value), label, description, displayValue(value));
    }

    private static String kindOf(@Nullable Object value) {
        if (value instanceof Map) {
            return "map";
        }
        if (value instanceof Collection) {
            return "list";
        }
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return "value";
        }
        return "object";
    }

    @Nullable
    private static String displayValue(@Nullable Object value) {
        if (value == null || value instanceof Map || value instanceof Collection) {
            return null;
        }
        return String.valueOf(TemplateFunctions.normalize(value));
    }

    @Nullable
    private static String labelOf(@Nullable Object value) {
        return value == null || value instanceof String || value instanceof Number || value instanceof Boolean ? null : String.valueOf(value);
    }

    private static List<Method> properties(Class<?> type) {
        return java.util.Arrays.stream(type.getMethods())
                               .filter(m -> m.getParameterCount() == 0 && !Modifier.isStatic(m.getModifiers()) && m.getDeclaringClass() != Object.class
                                       && !IGNORED.contains(m.getName()) && m.getReturnType() != void.class)
                               .sorted(Comparator.comparing(TemplateCatalog::propertyName))
                               .toList();
    }

    @Nullable
    private static Method getter(Class<?> type, String property) {
        return properties(type).stream().filter(m -> propertyName(m).equals(property)).findFirst().orElse(null);
    }

    private static String propertyName(Method method) {
        var name = method.getName();
        if (name.startsWith("get") && name.length() > 3) {
            return decapitalize(name.substring(3));
        }
        if (name.startsWith("is") && name.length() > 2 && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
            return decapitalize(name.substring(2));
        }
        return name;
    }

    private static String decapitalize(String name) {
        return name.length() > 1 && Character.isUpperCase(name.charAt(1)) ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    @Nullable
    private static Object invoke(Method method, Object target) {
        try {
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.debug("Template catalog could not read {}", method, e);
            return null;
        }
    }

    /** {@code wl.channel.get('a.b').level} → [wl, channel, get('a.b'), level]. */
    static List<String> split(String path) {
        var segments = new ArrayList<String>();
        var current = new StringBuilder();
        var quoted = false;
        for (var c : path.toCharArray()) {
            if (c == '\'') {
                quoted = !quoted;
            }
            if (c == '.' && !quoted) {
                if (!current.isEmpty()) {
                    segments.add(current.toString());
                }
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            segments.add(current.toString());
        }
        return segments;
    }
}
