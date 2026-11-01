package com.getpcpanel.integration.volume.platform.windows;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Every Java method {@code SndCtrl.dll} calls back is registered for JNI access. The DLL looks each one up by name and
 * signature ({@code GetMethodID}); in the native image only methods listed {@code jniAccessible} in the reachability
 * metadata can be found, and the DLL skips a call it cannot find. JVM and dev mode find every method, so only this
 * test notices one missing. The callbacks are read from the C++ sources, so a new callback cannot ship unregistered,
 * and one whose Java side was renamed or changed fails too.
 */
@DisplayName("native-image JNI registration of the SndCtrl.dll callbacks")
class SndCtrlJniCallbackCoverageTest {
    private static final String REACHABILITY_METADATA = "/META-INF/native-image/reachability-metadata.json";
    private static final Path CPP_SOURCES = Path.of("src", "main", "cpp", "SndCtrl");
    /** {@code jni->CallVoid(env, "name", "(sig)V", ...)} and the other {@code Call*} helpers of {@code JniCaller}. */
    private static final Pattern CALLBACK = Pattern.compile("\\bCall(?:Void|Object|ObjectFreeResult|Float|Boolean)\\(\\s*[^,]+,\\s*\"(\\w+)\",\\s*\"([^\"]+)\"");
    /** The objects the DLL holds and calls back on: the bean, each device and each session. */
    private static final List<Class<?>> RECEIVERS = List.of(SndCtrlWindows.class, WindowsAudioDevice.class, WindowsAudioSession.class);

    @Test
    void everyCallbackIsRegisteredForJni() throws Exception {
        var callbacks = callbacksInCppSources();
        assertTrue(callbacks.contains("deviceAdded (Ljava/lang/String;Ljava/lang/String;FZI)Lcom/getpcpanel/integration/volume/platform/AudioDevice;"),
                "sanity: expected to read deviceAdded from the C++ sources, found " + callbacks);
        var registered = jniRegisteredMethods();

        var problems = new TreeSet<String>();
        for (var callback : callbacks) {
            var name = callback.substring(0, callback.indexOf(' '));
            var descriptor = callback.substring(callback.indexOf(' ') + 1);
            var method = findCallback(name, descriptor);
            if (method == null) {
                problems.add(callback + ": no method with this name and signature on " + RECEIVERS);
                continue;
            }
            var key = registrationKey(method);
            if (!registered.contains(key)) {
                problems.add(callback + ": not registered, add to " + method.getDeclaringClass().getName() + " (jniAccessible) " + key);
            }
        }
        if (!problems.isEmpty()) {
            fail("SndCtrl.dll calls these back, but the native image cannot find them. Add each to the type's \"methods\" "
                 + "in " + REACHABILITY_METADATA + " (\"jniAccessible\": true):\n  " + String.join("\n  ", problems));
        }
    }

    private static Set<String> callbacksInCppSources() throws Exception {
        var result = new TreeSet<String>();
        try (Stream<Path> files = Files.list(CPP_SOURCES)) {
            for (var file : files.filter(p -> p.toString().endsWith(".cpp") || p.toString().endsWith(".h")).toList()) {
                var matcher = CALLBACK.matcher(Files.readString(file));
                while (matcher.find()) {
                    result.add(matcher.group(1) + " " + matcher.group(2));
                }
            }
        }
        return result;
    }

    private static Method findCallback(String name, String descriptor) {
        for (var receiver : RECEIVERS) {
            for (Class<?> c = receiver; c != null && c != Object.class; c = c.getSuperclass()) {
                for (var m : c.getDeclaredMethods()) {
                    if (m.getName().equals(name) && descriptor(m).equals(descriptor)) {
                        return m;
                    }
                }
            }
        }
        return null;
    }

    private static String descriptor(Method m) {
        return Stream.of(m.getParameterTypes()).map(SndCtrlJniCallbackCoverageTest::typeDescriptor).collect(Collectors.joining("", "(", ")"))
               + typeDescriptor(m.getReturnType());
    }

    private static String typeDescriptor(Class<?> type) {
        return type.describeConstable().orElseThrow().descriptorString();
    }

    private static String registrationKey(Method m) {
        return m.getDeclaringClass().getName() + "#" + m.getName()
               + Stream.of(m.getParameterTypes()).map(Class::getName).collect(Collectors.joining(",", "(", ")"));
    }

    private static Set<String> jniRegisteredMethods() throws Exception {
        var result = new HashSet<String>();
        try (InputStream in = SndCtrlJniCallbackCoverageTest.class.getResourceAsStream(REACHABILITY_METADATA)) {
            for (JsonNode entry : new ObjectMapper().readTree(in).path("reflection")) {
                if (!entry.path("jniAccessible").asBoolean()) {
                    continue;
                }
                var type = entry.path("type").asText();
                for (JsonNode method : entry.path("methods")) {
                    var params = new ArrayList<String>();
                    method.path("parameterTypes").forEach(p -> params.add(p.asText()));
                    result.add(type + "#" + method.path("name").asText() + "(" + String.join(",", params) + ")");
                }
            }
        }
        return result;
    }
}
