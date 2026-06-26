package com.getpcpanel.graalvm;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jna.Library;
import com.sun.jna.ptr.ByReference;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Discovery guard for the bug behind <b>#105</b>: a {@code com.sun.jna.ptr.*ByReference} type used in a
 * project JNA {@link Library} method signature is instantiated reflectively by JNA when it marshals the
 * call, so it must be registered for reflection in the native image. When it is not, the call throws
 * {@code IllegalArgumentException: Can't create an instance of class …ByReference, requires a public
 * no-arg constructor} (or {@code MissingReflectionRegistrationError}) — at runtime only, never in
 * JVM/dev mode. {@code CoreAudioLib.AudioObjectIsPropertySettable(…, ByteByReference)} is reached by
 * every macOS volume/mute write and shipped broken because {@code ByteByReference} was unregistered.
 *
 * <p>This walks every {@code com.getpcpanel.**} JNA {@link Library} interface, collects each
 * {@link ByReference} subtype appearing as a parameter or return type, and fails if any is absent from
 * the native-image reflection registrations. Like {@link ProxyRegistrationCoverageTest} it is
 * platform-independent: the Windows/Linux/macOS Library interfaces all sit on the classpath regardless
 * of build OS, so a forgotten registration is caught by CI on EVERY platform — the platform that
 * omitted it does not have to be the one running the test.
 */
@DisplayName("native-image JNA pointer (ByReference) registration coverage")
class JnaPointerRegistrationCoverageTest {

    private static final String REACHABILITY_METADATA = "/META-INF/native-image/reachability-metadata.json";

    @Test
    @DisplayName("every ByReference type in a project JNA Library signature is registered for reflection")
    void everyByReferenceInLibrarySignatureIsRegistered() throws Exception {
        var registered = readRegisteredReflectionTypeNames();
        assertTrue(registered.contains("com.sun.jna.ptr.IntByReference"),
                "sanity: registration metadata located (expected the known IntByReference registration)");

        var used = collectByReferenceTypesInLibrarySignatures();
        assertTrue(used.contains("com.sun.jna.ptr.IntByReference"),
                "sanity: expected to find at least IntByReference in a project Library signature, found " + used);

        var missing = new TreeSet<String>();
        for (var name : used) {
            if (!registered.contains(name)) {
                missing.add(name);
            }
        }

        if (!missing.isEmpty()) {
            var snippet = missing.stream().map(n -> "        \"" + n + "\",").reduce((a, b) -> a + "\n" + b).orElse("");
            fail("""
                    These com.sun.jna.ptr.*ByReference types appear in a project JNA Library method signature \
                    but are NOT registered for reflection. JNA instantiates them reflectively while marshalling \
                    the native call, so the call throws "Can't create an instance of class …ByReference, requires \
                    a public no-arg constructor" in the native image (works in JVM/dev). Register each in \
                    NativeImageConfig.classNames:
                    %s

                    Missing: %s""".formatted(snippet, missing));
        }
    }

    /** Every {@link ByReference} subtype used as a parameter or return type of a project JNA Library method. */
    private static Set<String> collectByReferenceTypesInLibrarySignatures() throws Exception {
        var result = new TreeSet<String>();
        for (var lib : findProjectJnaLibraryInterfaces()) {
            for (Method m : lib.getDeclaredMethods()) {
                addIfByReference(result, m.getReturnType());
                for (var p : m.getParameterTypes()) {
                    addIfByReference(result, p);
                }
            }
        }
        return result;
    }

    private static void addIfByReference(Set<String> sink, Class<?> type) {
        if (ByReference.class.isAssignableFrom(type) && !ByReference.class.equals(type)) {
            sink.add(type.getName());
        }
    }

    /**
     * Discovers every {@code com.getpcpanel.**} interface assignable to {@link Library} (the same scan as
     * {@link ProxyRegistrationCoverageTest}). Classes are loaded WITHOUT initialisation so {@code Native.load}
     * static initialisers never run.
     */
    private static Set<Class<?>> findProjectJnaLibraryInterfaces() throws Exception {
        var mainClassesRoot = projectMainClassesRoot();
        var packageRoot = mainClassesRoot.resolve("com").resolve("getpcpanel");
        var loader = JnaPointerRegistrationCoverageTest.class.getClassLoader();
        var result = new HashSet<Class<?>>();
        try (Stream<Path> walk = Files.walk(packageRoot)) {
            for (var classFile : walk.filter(p -> p.toString().endsWith(".class")).toList()) {
                Class<?> clazz;
                try {
                    clazz = Class.forName(toBinaryName(mainClassesRoot, classFile), false, loader);
                } catch (Throwable e) { // NoClassDefFoundError for optional platform deps etc.
                    continue;
                }
                if (clazz.isInterface() && Library.class.isAssignableFrom(clazz) && !Library.class.equals(clazz)) {
                    result.add(clazz);
                }
            }
        }
        return result;
    }

    /**
     * Union of every reflection registration the native image will see: the {@code targets()}/{@code classNames()}
     * of every {@link RegisterForReflection} in the project plus the reflection types in
     * {@code reachability-metadata.json}. Mirrors {@link ReflectionRegistrationCoverageTest}.
     */
    private static Set<String> readRegisteredReflectionTypeNames() throws Exception {
        var result = new HashSet<String>();
        var mainClassesRoot = projectMainClassesRoot();
        var loader = JnaPointerRegistrationCoverageTest.class.getClassLoader();
        try (Stream<Path> walk = Files.walk(mainClassesRoot.resolve("com").resolve("getpcpanel"))) {
            for (var classFile : walk.filter(p -> p.toString().endsWith(".class")).toList()) {
                Class<?> clazz;
                try {
                    clazz = Class.forName(toBinaryName(mainClassesRoot, classFile), false, loader);
                } catch (Throwable e) {
                    continue;
                }
                var ann = clazz.getAnnotation(RegisterForReflection.class);
                if (ann == null) {
                    continue;
                }
                for (var target : ann.targets()) {
                    result.add(target.getName());
                }
                result.addAll(List.of(ann.classNames()));
            }
        }

        var mapper = new ObjectMapper();
        try (InputStream in = JnaPointerRegistrationCoverageTest.class.getResourceAsStream(REACHABILITY_METADATA)) {
            if (in != null) {
                for (JsonNode entry : mapper.readTree(in).path("reflection")) {
                    var type = entry.path("type");
                    result.add(type.isTextual() ? type.asText() : type.path("name").asText());
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
