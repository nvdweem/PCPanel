package com.getpcpanel.graalvm;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
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
import com.getpcpanel.cpp.linux.LinuxKeyboard;
import com.sun.jna.Library;

/**
 * Discovery guard: finds missing native-image proxy registrations <em>without being told which to
 * look for</em>. Every JNA {@link Library} interface becomes a {@link java.lang.reflect.Proxy} at
 * runtime (via {@code Native.load}); in a native image each must be registered as a {@code proxy}
 * type or the first call throws {@code MissingReflectionRegistrationError}.
 *
 * <p>This is the test that would have <b>caught the LinuxKeyboard$X11 keystroke bug before it
 * shipped</b>: it scans the compiled {@code com.getpcpanel.**} classes for JNA {@link Library}
 * interfaces and fails if any is absent from the metadata — so it surfaces a forgotten proxy that
 * nobody knew about, unlike a hand-written test that only re-asserts a known interface name.
 *
 * <p>It is platform-independent on purpose: the Windows ({@code Win*}, {@code Voicemeeter}, …),
 * Linux ({@code LinuxKeyboard$X11/$XTest}) and macOS ({@code Osx*}, {@code CoreAudioLib}) interfaces
 * all sit on the classpath regardless of build OS, so a missing registration is caught by CI on
 * EVERY platform — the platform that omitted it does not have to be the one running the test. That
 * is precisely why the X11 omission slipped through: metadata was only ever regenerated on Windows.
 *
 * <p>Scope is limited to {@code com.getpcpanel.**}: third-party JNA libraries (hid4java,
 * {@code com.sun.jna.platform.win32.*}) are stable and already registered, and dbus
 * {@code DBusInterface}s are excluded because an <em>exported</em> dbus object is invoked
 * reflectively rather than through a proxy, so a blanket dbus rule would give false positives.
 */
@DisplayName("native-image proxy registration coverage")
class ProxyRegistrationCoverageTest {

    private static final String PROXY_CONFIG = "/META-INF/native-image/com.getpcpanel/pcpanel/proxy-config.json";
    private static final String REACHABILITY_METADATA = "/META-INF/native-image/reachability-metadata.json";

    @Test
    @DisplayName("every project JNA Library interface is registered as a native-image proxy")
    void everyJnaLibraryProxyIsRegistered() throws Exception {
        var registered = readRegisteredProxies();
        assertTrue(registered.contains("org.hid4java.jna.HidApiLibrary"),
                "sanity: metadata files were located and parsed (expected a known proxy entry)");

        var libraries = findProjectJnaLibraryInterfaces();
        assertTrue(libraries.size() >= 2,
                "sanity: expected to discover project JNA Library interfaces on the classpath, found " + libraries);

        var missing = new TreeSet<String>();
        for (var lib : libraries) {
            if (!registered.contains(lib)) {
                missing.add(lib);
            }
        }

        if (!missing.isEmpty()) {
            var snippet = missing.stream()
                    .map(name -> "  {\"interfaces\": [\"" + name + "\"]}")
                    .reduce((a, b) -> a + ",\n" + b)
                    .orElse("");
            fail("""
                    These JNA Library interfaces become runtime proxies but are NOT registered in the \
                    native-image metadata. They will throw MissingReflectionRegistrationError in the \
                    native image. Generate them with the tracing agent (see README) or, for platforms \
                    you cannot run the agent on, add each to proxy-config.json:
                    %s

                    Missing: %s""".formatted(snippet, missing));
        }
    }

    /** Union of proxy interface names from proxy-config.json (legacy) and reachability-metadata.json (unified). */
    private static Set<String> readRegisteredProxies() throws IOException {
        var mapper = new ObjectMapper();
        var result = new HashSet<String>();

        // Legacy proxy-config.json: [ {"interfaces": ["a.b.C"]}, ... ]
        try (InputStream in = resource(PROXY_CONFIG)) {
            for (JsonNode entry : mapper.readTree(in)) {
                for (JsonNode iface : entry.path("interfaces")) {
                    result.add(iface.asText());
                }
            }
        }

        // Unified reachability-metadata.json: { "reflection": [ {"type": {"proxy": ["a.b.C"]}}, ... ] }
        try (InputStream in = resource(REACHABILITY_METADATA)) {
            for (JsonNode entry : mapper.readTree(in).path("reflection")) {
                for (JsonNode iface : entry.path("type").path("proxy")) {
                    result.add(iface.asText());
                }
            }
        }
        return result;
    }

    private static InputStream resource(String path) throws IOException {
        var in = ProxyRegistrationCoverageTest.class.getResourceAsStream(path);
        if (in == null) {
            throw new IOException("native-image metadata not found on classpath: " + path);
        }
        return in;
    }

    /**
     * Discovers every {@code com.getpcpanel.**} interface assignable to {@link Library} by walking the
     * compiled main-class output. Classes are loaded WITHOUT initialisation so that {@code Native.load}
     * static initialisers never run (loading the real native library here would be flaky or impossible).
     */
    private static Set<String> findProjectJnaLibraryInterfaces() throws Exception {
        var mainClassesRoot = projectMainClassesRoot();
        var packageRoot = mainClassesRoot.resolve("com").resolve("getpcpanel");
        var loader = ProxyRegistrationCoverageTest.class.getClassLoader();
        var result = new TreeSet<String>();
        try (Stream<Path> walk = Files.walk(packageRoot)) {
            List<Path> classFiles = walk.filter(p -> p.toString().endsWith(".class")).toList();
            for (var classFile : classFiles) {
                var binaryName = toBinaryName(mainClassesRoot, classFile);
                Class<?> clazz;
                try {
                    clazz = Class.forName(binaryName, false, loader);
                } catch (Throwable e) { // NoClassDefFoundError for optional deps etc. — not a proxy candidate
                    continue;
                }
                if (clazz.isInterface() && Library.class.isAssignableFrom(clazz) && !Library.class.equals(clazz)) {
                    result.add(clazz.getName());
                }
            }
        }
        return result;
    }

    /** The {@code target/classes} root of the MAIN sources (not {@code test-classes}), via a known main class. */
    private static Path projectMainClassesRoot() throws Exception {
        var location = LinuxKeyboard.class.getProtectionDomain().getCodeSource().getLocation();
        return Path.of(location.toURI());
    }

    private static String toBinaryName(Path classesRoot, Path classFile) {
        var relative = classesRoot.relativize(classFile).toString();
        return relative.substring(0, relative.length() - ".class".length())
                .replace(java.io.File.separatorChar, '.');
    }
}
