package com.getpcpanel.graalvm;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.commands.command.Command;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Discovery guard: finds missing native-image <em>reflection</em> registrations <em>without being told
 * which to look for</em>. It walks the Jackson-serialised property graph from two root sets — the
 * {@link Command} hierarchy (serialised into the profile and the WebSocket device snapshots) and the
 * return types of every JAX-RS resource method — and fails on any reachable project type the native
 * image is not told it needs reflective access to.
 *
 * <p>Every {@link Command} subtype is serialised to/from JSON via {@code @JsonTypeInfo(use=Id.NAME)}
 * with an explicit subtype allowlist — each command package contributes a {@code CommandModule} bean,
 * and {@code CommandSubtypeRegistrar} registers the listed subclasses on the ObjectMapper (the
 * per-control command maps in the profile, and the device snapshots pushed over the WebSocket).
 * In a native image Jackson invokes each type's accessors reflectively, so every concrete subtype —
 * and every project type it transitively serialises — must be registered for reflection or the first
 * serialisation throws {@code MissingReflectionRegistrationError}, which kills the WebSocket
 * {@code @OnOpen} handler and leaves the UI stuck on "Connecting" (works fine in JVM/dev, which always
 * has reflection).
 *
 * <p>This is the test that would have <b>caught the {@code CommandAnalogBands}/{@code AnalogBand} bug
 * before it shipped</b>: it scans the compiled {@code com.getpcpanel.**} classes for concrete
 * {@link Command} subtypes, walks the Jackson-serialised property graph reachable from each, and fails
 * if any reachable project type is absent from the metadata — so it surfaces a forgotten command (or a
 * forgotten record nested inside one) that nobody knew about, unlike a hand-written test that only
 * re-asserts known type names.
 *
 * <h2>Rules it encodes (mirroring the GraalVM notes in CLAUDE.md)</h2>
 * <ul>
 *   <li>Every concrete {@link Command} subtype must be registered.</li>
 *   <li>Every <em>concrete</em> project class/record reachable as a Jackson-serialised property must be
 *       registered.</li>
 *   <li>A {@code List}/{@code Set}/array of a <em>concrete</em> project element type additionally needs
 *       its array type registered ({@code Foo[].class}) — Jackson reflectively instantiates the array
 *       per collection during serialisation. Polymorphic element types (abstract {@link Command}) do
 *       <b>not</b> need an array form; their concrete subtypes are covered as roots.</li>
 * </ul>
 * Enums and interfaces/abstract types are not themselves required here (enums serialise by name; the
 * concrete leaves of abstract hierarchies are discovered separately as roots).
 */
@DisplayName("native-image reflection registration coverage (Command hierarchy + REST responses)")
class ReflectionRegistrationCoverageTest {

    private static final String REACHABILITY_METADATA = "/META-INF/native-image/reachability-metadata.json";
    private static final Set<String> PROJECT_PACKAGES = Set.of("com.getpcpanel.", "dev.niels.");

    @Test
    @DisplayName("every Command subtype and the project types it serialises are registered for reflection")
    void everySerialisedCommandTypeIsRegistered() throws Exception {
        var registered = readRegisteredReflectionTypeNames();
        assertTrue(registered.contains(Command.class.getName()),
                "sanity: expected the registered set (from @RegisterForReflection + reachability-metadata.json) "
                        + "to contain the Command base type");

        var roots = findConcreteCommandSubtypes();
        assertTrue(roots.size() >= 10,
                "sanity: expected to discover the concrete Command subtypes on the classpath, found " + roots.size());

        var mapper = new ObjectMapper();
        var missing = new TreeSet<String>();
        Deque<Class<?>> queue = new ArrayDeque<>(roots);

        // Every concrete Command subtype must itself be registered.
        for (var root : roots) {
            requireType(root, registered, missing);
        }

        walkSerialisedGraph(queue, mapper, registered, missing);

        if (!missing.isEmpty()) {
            fail("""
                    These project types are reachable through Jackson serialisation of the Command \
                    hierarchy but are NOT registered for native-image reflection. They will throw \
                    MissingReflectionRegistrationError in the native image (e.g. when the WebSocket pushes \
                    the initial device snapshot), even though serialisation works in JVM/dev mode. \
                    Add each to the @RegisterForReflection targets in NativeImageConfig — and for a List/Set \
                    of a concrete type, add BOTH the element type AND its array form (Foo.class, Foo[].class):

                    Missing:
                    %s""".formatted(missing.stream().map(n -> "  " + n).reduce((a, b) -> a + "\n" + b).orElse("")));
        }
    }

    /**
     * Same guard, rooted at the REST layer instead of the {@link Command} hierarchy: every project type
     * a resource method returns must be declared, and a {@code List<Foo>} must declare {@code Foo[]} too.
     *
     * <p>Quarkus registers REST signature types itself, so some of what this requires is redundant in
     * practice. Requiring it regardless is the point: framework auto-registration is an implementation
     * detail nothing here asserts, and it has already proven insufficient for array forms
     * (WaveLinkResponseDto needed them spelled out). Declaring a type that is already reachable costs
     * nothing at runtime, and it turns "hopefully Quarkus covers this" into an invariant the build checks.
     *
     * <p>The alternative — hitting the endpoints — cannot do this job: Jackson only instantiates the
     * element array once a list is non-empty, and these lists are hardware- and audio-dependent, so CI
     * sees them empty and passes while a user with real data gets the 500. Walking types instead needs no
     * device, no Wave Link server and no native build.
     */
    @Test
    @DisplayName("every project type returned by a REST resource is registered for reflection")
    void everySerialisedRestResponseTypeIsRegistered() throws Exception {
        var registered = readRegisteredReflectionTypeNames();
        var mapper = new ObjectMapper();
        var missing = new TreeSet<String>();
        Deque<Class<?>> queue = new ArrayDeque<>();

        var returnTypes = findRestResponseTypes();
        assertTrue(returnTypes.size() >= 10,
                "sanity: expected to discover the JAX-RS resource method return types on the classpath, found "
                        + returnTypes.size());

        for (var returnType : returnTypes) {
            inspectType(mapper.constructType(returnType), registered, missing, queue);
        }
        walkSerialisedGraph(queue, mapper, registered, missing);

        if (!missing.isEmpty()) {
            fail("""
                    These project types are reachable through Jackson serialisation of a REST response but \
                    are NOT declared for native-image reflection. Add each to the @RegisterForReflection \
                    targets in NativeImageConfig (or on the response DTO) — and for a List/Set of a concrete \
                    type, add BOTH the element type AND its array form (Foo.class, Foo[].class). Declare them \
                    even if Quarkus would register them anyway: it costs nothing and keeps the requirement \
                    checkable rather than resting on framework behaviour.

                    Missing:
                    %s""".formatted(missing.stream().map(n -> "  " + n).reduce((a, b) -> a + "\n" + b).orElse("")));
        }
    }

    /**
     * Breadth-first walk of the Jackson-serialised property graph reachable from the queued roots,
     * recording every concrete project type that is not registered.
     */
    private static void walkSerialisedGraph(Deque<Class<?>> queue, ObjectMapper mapper,
            Set<String> registered, Set<String> missing) {
        var visited = new HashSet<Class<?>>();
        while (!queue.isEmpty()) {
            var clazz = queue.poll();
            if (!visited.add(clazz)) {
                continue;
            }
            for (var prop : mapper.getSerializationConfig().introspect(mapper.constructType(clazz)).findProperties()) {
                inspectType(prop.getPrimaryType(), registered, missing, queue);
            }
        }
    }

    /**
     * Generic return types of every JAX-RS resource method in the project. Verbs are found via the
     * {@link HttpMethod} meta-annotation, so this covers {@code @GET}/{@code @POST}/{@code @PUT}/
     * {@code @DELETE} alike. {@code void} and {@link Response} say nothing about the serialised body, so
     * they are skipped — an endpoint that hand-builds a {@code Response} is outside what this can see.
     */
    private static Set<Type> findRestResponseTypes() throws Exception {
        var mainClassesRoot = projectMainClassesRoot();
        var loader = ReflectionRegistrationCoverageTest.class.getClassLoader();
        var result = new HashSet<Type>();
        try (Stream<Path> walk = Files.walk(mainClassesRoot.resolve("com").resolve("getpcpanel"))) {
            for (var classFile : walk.filter(p -> p.toString().endsWith(".class")).toList()) {
                Class<?> clazz;
                try {
                    clazz = Class.forName(toBinaryName(mainClassesRoot, classFile), false, loader);
                } catch (Throwable e) { // NoClassDefFoundError for optional platform deps etc.
                    continue;
                }
                if (!clazz.isAnnotationPresent(jakarta.ws.rs.Path.class)) {
                    continue;
                }
                for (var method : clazz.getMethods()) {
                    if (isResourceMethod(method) && method.getReturnType() != void.class
                            && method.getReturnType() != Response.class) {
                        result.add(method.getGenericReturnType());
                    }
                }
            }
        }
        return result;
    }

    private static boolean isResourceMethod(Method method) {
        for (var annotation : method.getAnnotations()) {
            if (annotation.annotationType().isAnnotationPresent(HttpMethod.class)) {
                return true;
            }
        }
        return false;
    }

    /** Require a single (possibly array) type to be registered, recording a violation if it is not. */
    private static void requireType(Class<?> clazz, Set<String> registered, Set<String> missing) {
        if (!registered.contains(clazz.getName()) && !registered.contains(canonicalName(clazz))) {
            missing.add(canonicalName(clazz));
        }
    }

    /**
     * Inspects a Jackson property type: unwraps collections/arrays/maps, requires concrete project
     * element types (and their array form when they came from a collection/array), and enqueues
     * concrete project types for further graph traversal.
     */
    private static void inspectType(JavaType type, Set<String> registered, Set<String> missing, Deque<Class<?>> queue) {
        if (type.isArrayType()) {
            requireElement(type.getContentType(), true, registered, missing, queue);
        } else if (type.isCollectionLikeType()) {
            requireElement(type.getContentType(), true, registered, missing, queue);
        } else if (type.isMapLikeType()) {
            // Map values can be project types (e.g. Map<Integer, Commands>); the key is virtually always
            // a String/Integer. Map values do not need an array form.
            requireElement(type.getContentType(), false, registered, missing, queue);
        } else {
            requireElement(type, false, registered, missing, queue);
        }
    }

    private static void requireElement(JavaType element, boolean needsArrayForm, Set<String> registered,
            Set<String> missing, Deque<Class<?>> queue) {
        var raw = element.getRawClass();
        if (!isProjectType(raw) || raw.isInterface() || Modifier.isAbstract(raw.getModifiers()) || raw.isEnum()) {
            // Not a concrete project type: JDK/third-party (skip), or an abstract/polymorphic project type
            // whose concrete leaves are discovered as roots (so no array form is needed for it either).
            return;
        }
        requireType(raw, registered, missing);
        if (needsArrayForm) {
            requireType(raw.arrayType(), registered, missing);
        }
        queue.add(raw); // recurse into its own serialised properties
    }

    /** Canonical, human-readable name ({@code com.foo.Bar[]} rather than {@code [Lcom.foo.Bar;}). */
    private static String canonicalName(Class<?> clazz) {
        return clazz.isArray() ? canonicalName(clazz.getComponentType()) + "[]" : clazz.getName();
    }

    private static boolean isProjectType(Class<?> clazz) {
        var name = clazz.getName();
        return PROJECT_PACKAGES.stream().anyMatch(name::startsWith);
    }

    /**
     * Union of every reflection registration the native image will see: the {@code targets()} and
     * {@code classNames()} of every {@link RegisterForReflection} annotation in the project, plus the
     * reflection types listed in {@code reachability-metadata.json}. Names are stored in both binary
     * ({@code [Lcom.foo.Bar;}) and canonical ({@code com.foo.Bar[]}) array forms for easy matching.
     */
    private static Set<String> readRegisteredReflectionTypeNames() throws Exception {
        var result = new HashSet<String>();
        var mainClassesRoot = projectMainClassesRoot();
        var loader = ReflectionRegistrationCoverageTest.class.getClassLoader();

        try (Stream<Path> walk = Files.walk(mainClassesRoot.resolve("com").resolve("getpcpanel"))) {
            List<Path> classFiles = walk.filter(p -> p.toString().endsWith(".class")).toList();
            for (var classFile : classFiles) {
                Class<?> clazz;
                try {
                    clazz = Class.forName(toBinaryName(mainClassesRoot, classFile), false, loader);
                } catch (Throwable e) { // NoClassDefFoundError for optional platform deps etc.
                    continue;
                }
                var ann = clazz.getAnnotation(RegisterForReflection.class);
                if (ann == null) {
                    continue;
                }
                for (var target : ann.targets()) {
                    result.add(target.getName());
                    result.add(canonicalName(target));
                }
                result.addAll(List.of(ann.classNames()));
            }
        }

        var mapper = new ObjectMapper();
        try (InputStream in = ReflectionRegistrationCoverageTest.class.getResourceAsStream(REACHABILITY_METADATA)) {
            if (in != null) {
                for (JsonNode entry : mapper.readTree(in).path("reflection")) {
                    var type = entry.path("type");
                    result.add(type.isTextual() ? type.asText() : type.path("name").asText());
                }
            }
        }
        return result;
    }

    /** Discovers every concrete {@code com.getpcpanel.**} subclass of {@link Command}. */
    private static Set<Class<?>> findConcreteCommandSubtypes() throws Exception {
        var mainClassesRoot = projectMainClassesRoot();
        var loader = ReflectionRegistrationCoverageTest.class.getClassLoader();
        var result = new HashSet<Class<?>>();
        try (Stream<Path> walk = Files.walk(mainClassesRoot.resolve("com").resolve("getpcpanel"))) {
            for (var classFile : walk.filter(p -> p.toString().endsWith(".class")).toList()) {
                Class<?> clazz;
                try {
                    clazz = Class.forName(toBinaryName(mainClassesRoot, classFile), false, loader);
                } catch (Throwable e) {
                    continue;
                }
                if (Command.class.isAssignableFrom(clazz) && !clazz.isInterface()
                        && !Modifier.isAbstract(clazz.getModifiers())) {
                    result.add(clazz);
                }
            }
        }
        return result;
    }

    private static Path projectMainClassesRoot() throws Exception {
        var location = NativeImageConfig.class.getProtectionDomain().getCodeSource().getLocation();
        return Path.of(location.toURI());
    }

    private static String toBinaryName(Path classesRoot, Path classFile) {
        var relative = classesRoot.relativize(classFile).toString();
        return relative.substring(0, relative.length() - ".class".length())
                .replace(java.io.File.separatorChar, '.');
    }
}
