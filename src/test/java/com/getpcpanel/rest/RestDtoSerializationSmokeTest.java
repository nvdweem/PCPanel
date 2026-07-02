package com.getpcpanel.rest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentest4j.TestAbortedException;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.commands.CommandModule;
import com.getpcpanel.commands.CommandSubtypeRegistrar;

import jakarta.ws.rs.core.Response;

/**
 * REST serialization smoke: catches "compiles but 500s at serialization" wiring breaks across the
 * DTO-returning endpoints without booting the app (a {@code @QuarkusTest} is unsafe here —
 * {@code DeviceProviderRegistry} starts every device provider on {@code StartupEvent}, which scans
 * real HID/serial/MIDI hardware, and there is no {@code %test} config gate for it).
 *
 * <p>Discovery is reflective so new endpoints are covered automatically: every compiled
 * {@code com.getpcpanel.**} class annotated {@code @jakarta.ws.rs.Path} is a resource; every HTTP
 * method on it whose return type is a project DTO (or a collection of one) contributes that type.
 * For each type a fully <em>populated</em> instance is built (non-null fields, single-element
 * collections — an empty list would hide exactly the class of bug this guards) and serialized
 * through an ObjectMapper configured like the app's (registered modules +
 * {@link CommandSubtypeRegistrar} with every {@link CommandModule} on the classpath +
 * {@code fail-on-unknown-properties=false}).
 *
 * <p>Types the dummy-value builder cannot instantiate are <em>skipped</em> with a clear message
 * (via {@link TestAbortedException}) rather than failed, so an exotic DTO shape can't turn this
 * guard into noise; a sanity floor asserts the discovery keeps finding a healthy number of
 * endpoints.
 */
@DisplayName("REST DTO serialization smoke (all @Path resources)")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RestDtoSerializationSmokeTest {
    private static final int MAX_DEPTH = 10;

    private final List<Class<?>> allProjectClasses = scanProjectClasses();
    private final ObjectMapper mapper = buildAppLikeMapper(allProjectClasses);

    /** One entry per resource method whose (unwrapped) return type is serialized by Jackson. */
    record Endpoint(String description, Type returnType) {
        @Override
        public String toString() {
            return description;
        }
    }

    Stream<Endpoint> endpoints() {
        var result = new ArrayList<Endpoint>();
        for (var resource : allProjectClasses) {
            if (!resource.isAnnotationPresent(jakarta.ws.rs.Path.class)) {
                continue;
            }
            Method[] methods;
            try {
                methods = resource.getDeclaredMethods();
            } catch (Throwable e) { // optional platform deps in signatures
                continue;
            }
            for (var method : methods) {
                if (!isHttpMethod(method)) {
                    continue;
                }
                var returnType = unwrapAsync(method.getGenericReturnType());
                if (!isJacksonSerialized(returnType)) {
                    continue;
                }
                var sub = method.getAnnotation(jakarta.ws.rs.Path.class);
                var path = resource.getAnnotation(jakarta.ws.rs.Path.class).value() + (sub == null ? "" : "/" + sub.value());
                result.add(new Endpoint(httpMethodName(method) + " " + path.replaceAll("/+", "/") + " (" + resource.getSimpleName() + "#" + method.getName() + ")",
                        returnType));
            }
        }
        result.sort(Comparator.comparing(Endpoint::description));
        return result.stream();
    }

    @Test
    @DisplayName("discovery sanity: a healthy number of DTO-returning endpoints is found")
    void discoveryFindsEndpoints() {
        var count = endpoints().count();
        assertTrue(count >= 15, "expected at least 15 DTO-returning endpoints, found " + count
                + " — the reflective discovery is broken, not the endpoints");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    @DisplayName("a populated response DTO serializes without throwing")
    void populatedDtoSerializes(Endpoint endpoint) throws Exception {
        Object value;
        try {
            value = build(endpoint.returnType(), new ArrayDeque<>());
        } catch (Throwable e) {
            throw new TestAbortedException("SKIPPED (cannot build a dummy instance of " + endpoint.returnType().getTypeName()
                    + "): " + e, e);
        }
        var json = mapper.writeValueAsString(value);
        assertFalse(json.isEmpty(), "expected JSON output for " + endpoint);
    }

    // ── discovery helpers ─────────────────────────────────────────────────────

    private static boolean isHttpMethod(Method method) {
        return method.isAnnotationPresent(jakarta.ws.rs.GET.class)
                || method.isAnnotationPresent(jakarta.ws.rs.POST.class)
                || method.isAnnotationPresent(jakarta.ws.rs.PUT.class)
                || method.isAnnotationPresent(jakarta.ws.rs.DELETE.class)
                || method.isAnnotationPresent(jakarta.ws.rs.PATCH.class);
    }

    private static String httpMethodName(Method method) {
        if (method.isAnnotationPresent(jakarta.ws.rs.GET.class))
            return "GET";
        if (method.isAnnotationPresent(jakarta.ws.rs.POST.class))
            return "POST";
        if (method.isAnnotationPresent(jakarta.ws.rs.PUT.class))
            return "PUT";
        if (method.isAnnotationPresent(jakarta.ws.rs.DELETE.class))
            return "DELETE";
        return "PATCH";
    }

    /** Unwraps {@code CompletionStage<X>}/{@code Uni<X>}-style async wrappers to the payload type. */
    private static Type unwrapAsync(Type type) {
        if (type instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> raw
                && (java.util.concurrent.CompletionStage.class.isAssignableFrom(raw) || raw.getName().endsWith(".Uni"))) {
            return pt.getActualTypeArguments()[0];
        }
        return type;
    }

    /** Whether the return type is a body Jackson serializes (not void/Response/streaming/plain text). */
    private static boolean isJacksonSerialized(Type type) {
        var raw = rawClass(type);
        if (raw == null || raw == void.class || raw == Void.class || raw.isPrimitive()) {
            return false;
        }
        return !Response.class.isAssignableFrom(raw)
                && raw != String.class
                && raw != byte[].class
                && !InputStream.class.isAssignableFrom(raw)
                && !File.class.isAssignableFrom(raw);
    }

    private static List<Class<?>> scanProjectClasses() {
        try {
            var classesRoot = Path.of(CommandModule.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            var loader = RestDtoSerializationSmokeTest.class.getClassLoader();
            var result = new ArrayList<Class<?>>();
            try (Stream<Path> walk = Files.walk(classesRoot.resolve("com").resolve("getpcpanel"))) {
                for (var classFile : walk.filter(p -> p.toString().endsWith(".class")).toList()) {
                    var relative = classesRoot.relativize(classFile).toString();
                    var binaryName = relative.substring(0, relative.length() - ".class".length()).replace(File.separatorChar, '.');
                    try {
                        result.add(Class.forName(binaryName, false, loader));
                    } catch (Throwable e) { // optional platform deps etc.
                    }
                }
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("cannot scan project classes", e);
        }
    }

    /** The app's mapper shape: registered datatype modules + the command-subtype customizer. */
    private static ObjectMapper buildAppLikeMapper(List<Class<?>> allProjectClasses) {
        var mapper = new ObjectMapper().findAndRegisterModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var modules = new ArrayList<CommandModule>();
        for (var clazz : allProjectClasses) {
            if (CommandModule.class.isAssignableFrom(clazz) && !clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers())) {
                try {
                    modules.add((CommandModule) clazz.getDeclaredConstructor().newInstance());
                } catch (Exception e) {
                    throw new IllegalStateException("CommandModule " + clazz.getName() + " is not no-arg instantiable", e);
                }
            }
        }
        assertTrue(modules.size() >= 5, "expected the CommandModule scan to find the feature modules, found " + modules.size());
        try {
            var registrar = new CommandSubtypeRegistrar();
            var field = CommandSubtypeRegistrar.class.getDeclaredField("modules");
            field.setAccessible(true);
            field.set(registrar, modules);
            registrar.customize(mapper);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot wire CommandSubtypeRegistrar", e);
        }
        return mapper;
    }

    // ── populated dummy-instance builder ──────────────────────────────────────

    private Object build(Type type, Deque<Class<?>> stack) throws Exception {
        if (stack.size() > MAX_DEPTH) {
            return null;
        }
        if (type instanceof WildcardType wt) {
            return build(wt.getUpperBounds()[0], stack);
        }
        if (type instanceof GenericArrayType gat) {
            var element = build(gat.getGenericComponentType(), stack);
            var array = java.lang.reflect.Array.newInstance(rawClass(gat.getGenericComponentType()), 1);
            java.lang.reflect.Array.set(array, 0, element);
            return array;
        }
        if (type instanceof ParameterizedType pt) {
            var raw = (Class<?>) pt.getRawType();
            var args = pt.getActualTypeArguments();
            if (Map.class.isAssignableFrom(raw)) {
                var map = new LinkedHashMap<>();
                map.put(build(args[0], stack), build(args[1], stack));
                return map;
            }
            if (Set.class.isAssignableFrom(raw)) {
                return Set.of(build(args[0], stack));
            }
            if (Collection.class.isAssignableFrom(raw) || Iterable.class.isAssignableFrom(raw)) {
                return List.of(build(args[0], stack));
            }
            if (Optional.class.isAssignableFrom(raw)) {
                return Optional.ofNullable(build(args[0], stack));
            }
            return buildBean(raw, stack);
        }
        var raw = rawClass(type);
        if (raw == null) {
            return null;
        }
        var wellKnown = wellKnownValue(raw);
        if (wellKnown != null) {
            return wellKnown;
        }
        if (raw.isEnum()) {
            var constants = raw.getEnumConstants();
            return constants.length == 0 ? null : constants[0];
        }
        if (raw.isArray()) {
            var element = build(raw.getComponentType(), stack);
            var array = java.lang.reflect.Array.newInstance(raw.getComponentType(), 1);
            java.lang.reflect.Array.set(array, 0, element);
            return array;
        }
        if (Map.class.isAssignableFrom(raw)) {
            return Map.of("key", "value");
        }
        if (Collection.class.isAssignableFrom(raw)) {
            return List.of("value");
        }
        return buildBean(raw, stack);
    }

    @SuppressWarnings("UseOfObsoleteDateTimeApi")
    private static Object wellKnownValue(Class<?> raw) {
        if (raw == String.class || raw == Object.class || raw == CharSequence.class)
            return "value";
        if (raw == int.class || raw == Integer.class)
            return 1;
        if (raw == long.class || raw == Long.class)
            return 1L;
        if (raw == double.class || raw == Double.class)
            return 1.0d;
        if (raw == float.class || raw == Float.class)
            return 1.0f;
        if (raw == short.class || raw == Short.class)
            return (short) 1;
        if (raw == byte.class || raw == Byte.class)
            return (byte) 1;
        if (raw == boolean.class || raw == Boolean.class)
            return Boolean.TRUE;
        if (raw == char.class || raw == Character.class)
            return 'x';
        if (raw == BigDecimal.class)
            return BigDecimal.ONE;
        if (raw == UUID.class)
            return UUID.fromString("00000000-0000-0000-0000-000000000001");
        if (raw == File.class)
            return new File("dummy.txt");
        if (raw == Path.class)
            return Path.of("dummy.txt");
        if (raw == URI.class)
            return URI.create("http://localhost/");
        if (raw == Instant.class)
            return Instant.EPOCH;
        if (raw == Duration.class)
            return Duration.ofSeconds(1);
        if (raw == LocalDate.class)
            return LocalDate.EPOCH;
        if (raw == LocalDateTime.class)
            return LocalDateTime.of(1970, 1, 1, 0, 0);
        if (raw == OffsetDateTime.class)
            return OffsetDateTime.parse("1970-01-01T00:00:00Z");
        if (raw == Date.class)
            return new Date(0);
        return null;
    }

    /** Builds a project bean/record: records via canonical ctor, else no-arg ctor + populated fields. */
    private Object buildBean(Class<?> raw, Deque<Class<?>> stack) throws Exception {
        if (stack.contains(raw)) {
            return null; // cyclic reference — leave the inner occurrence null
        }
        stack.push(raw);
        try {
            var concrete = raw;
            if (raw.isInterface() || Modifier.isAbstract(raw.getModifiers())) {
                concrete = findConcreteSubtype(raw);
                if (concrete == null) {
                    throw new IllegalStateException("no concrete subtype found for " + raw.getName());
                }
            }
            if (concrete.isRecord()) {
                var components = concrete.getRecordComponents();
                var types = new Class<?>[components.length];
                var args = new Object[components.length];
                for (var i = 0; i < components.length; i++) {
                    types[i] = components[i].getType();
                    args[i] = build(components[i].getGenericType(), stack);
                }
                var ctor = concrete.getDeclaredConstructor(types);
                ctor.setAccessible(true);
                return ctor.newInstance(args);
            }
            Constructor<?> noArg = null;
            try {
                noArg = concrete.getDeclaredConstructor();
            } catch (NoSuchMethodException ignored) {
            }
            if (noArg != null) {
                noArg.setAccessible(true);
                var instance = noArg.newInstance();
                populateFields(instance, concrete, stack);
                return instance;
            }
            // no no-arg ctor: use the greediest constructor with recursively built arguments
            var ctor = Stream.of(concrete.getDeclaredConstructors())
                    .max(Comparator.comparingInt(Constructor::getParameterCount))
                    .orElseThrow(() -> new IllegalStateException("no constructor on " + raw.getName()));
            ctor.setAccessible(true);
            var params = ctor.getGenericParameterTypes();
            var args = new Object[params.length];
            for (var i = 0; i < params.length; i++) {
                args[i] = build(params[i], stack);
            }
            return ctor.newInstance(args);
        } finally {
            stack.pop();
        }
    }

    /** Fills every instance field with a built value so serialization exercises populated state. */
    private void populateFields(Object instance, Class<?> concrete, Deque<Class<?>> stack) {
        for (var current = concrete; current != null && current != Object.class; current = current.getSuperclass()) {
            for (var field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    var value = build(field.getGenericType(), stack);
                    if (value != null) {
                        field.set(instance, value);
                    }
                } catch (Throwable ignored) { // a field that cannot be filled stays at its default
                }
            }
        }
    }

    /** First instantiable concrete subtype on the classpath, preferring the fewest-field one. */
    private Class<?> findConcreteSubtype(Class<?> base) {
        return allProjectClasses.stream()
                .filter(base::isAssignableFrom)
                .filter(c -> !c.isInterface() && !Modifier.isAbstract(c.getModifiers()) && !c.isAnonymousClass())
                .min(Comparator.comparingInt(c -> c.getDeclaredFields().length))
                .orElse(null);
    }

    private static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> c) {
            return c;
        }
        if (type instanceof ParameterizedType pt) {
            return (Class<?>) pt.getRawType();
        }
        if (type instanceof GenericArrayType gat) {
            var component = rawClass(gat.getGenericComponentType());
            return component == null ? null : component.arrayType();
        }
        return null;
    }
}
