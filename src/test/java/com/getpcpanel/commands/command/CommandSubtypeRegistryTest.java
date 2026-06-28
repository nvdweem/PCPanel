package com.getpcpanel.commands.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Guards the {@link Command} polymorphic type registry.
 *
 * <p>{@code Command} uses {@code Id.NAME} + {@link JsonSubTypes}: each subtype's {@code name} is a
 * stable, location-independent id that equals the class's historical fully-qualified name, which is
 * exactly what existing {@code profiles.json} files persist. This lets a command class move into its
 * own feature package without changing the persisted {@code _type}. These tests enforce the two
 * invariants that keep that safe:
 *
 * <ol>
 *   <li><b>Completeness</b> — every concrete {@code Command} subtype on the classpath is registered
 *       (a missing entry would make Jackson throw at serialization time), and there are no stale
 *       entries. This is the "you forgot to register your new command" guard.</li>
 *   <li><b>Migration</b> — every registered persisted id still deserializes to its class, so saved
 *       profiles keep loading after a command moves package.</li>
 * </ol>
 */
@DisplayName("Command @JsonSubTypes registry")
class CommandSubtypeRegistryTest {
    // Mirror the Quarkus production mapper, which does not fail on unknown properties — commands
    // serialize extra getters (e.g. a dial command's top-level `invert`) that have no creator param.
    private final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    @DisplayName("every concrete Command subtype is registered exactly once, with a unique id")
    void registryIsCompleteAndConsistent() throws Exception {
        var registered = registeredSubtypes();
        var concrete = findConcreteCommandSubtypes();

        var missing = concrete.stream().filter(c -> !registered.containsKey(c)).map(Class::getName).sorted().toList();
        assertTrue(missing.isEmpty(), "Concrete Command subtypes missing from Command's @JsonSubTypes (add an @Type line): " + missing);

        var stale = registered.keySet().stream().filter(c -> !concrete.contains(c)).map(Class::getName).sorted().toList();
        assertTrue(stale.isEmpty(), "@JsonSubTypes entries that are not concrete Command subtypes (remove them): " + stale);

        var names = new HashSet<String>();
        for (var name : registered.values()) {
            assertFalse(name.isBlank(), "Blank @JsonSubTypes name");
            assertTrue(names.add(name), "Duplicate @JsonSubTypes name: " + name);
        }
    }

    @Test
    @DisplayName("every persisted type id deserializes back to its command class")
    void persistedTypeIdsResolve() {
        registeredSubtypes().forEach((clazz, name) -> {
            var json = "{\"_type\":\"" + name + "\"}";
            try {
                var cmd = mapper.readValue(json, Command.class);
                assertInstanceOf(clazz, cmd, () -> "id '" + name + "' resolved to the wrong class");
            } catch (Exception e) {
                fail("Persisted type id '" + name + "' (for " + clazz.getName() + ") no longer deserializes: " + e.getMessage());
            }
        });
    }

    @Test
    @DisplayName("a CommandVolumeProcess persists and reloads under its frozen id")
    void roundTripFrozenId() throws Exception {
        var json = mapper.writeValueAsString(new CommandVolumeProcess(java.util.List.of("foo.exe"), "", false, DialAction.DialCommandParams.DEFAULT));
        assertTrue(json.contains("\"com.getpcpanel.commands.command.CommandVolumeProcess\""), "frozen id not emitted: " + json);
        assertInstanceOf(CommandVolumeProcess.class, mapper.readValue(json, Command.class));
    }

    private static Map<Class<?>, String> registeredSubtypes() {
        var ann = Command.class.getAnnotation(JsonSubTypes.class);
        assertEquals(JsonSubTypes.class, ann.annotationType());
        var result = new HashMap<Class<?>, String>();
        for (var t : ann.value()) {
            result.put(t.value(), t.name());
        }
        return result;
    }

    /** Discovers every concrete {@code com.getpcpanel.**} subclass of {@link Command} from the built classes. */
    private static Set<Class<?>> findConcreteCommandSubtypes() throws Exception {
        var root = Path.of(Command.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var loader = CommandSubtypeRegistryTest.class.getClassLoader();
        var result = new HashSet<Class<?>>();
        try (Stream<Path> walk = Files.walk(root.resolve("com").resolve("getpcpanel"))) {
            for (var classFile : walk.filter(p -> p.toString().endsWith(".class")).toList()) {
                var binary = root.relativize(classFile).toString();
                binary = binary.substring(0, binary.length() - ".class".length()).replace(java.io.File.separatorChar, '.');
                Class<?> clazz;
                try {
                    clazz = Class.forName(binary, false, loader);
                } catch (Throwable e) {
                    continue;
                }
                if (Command.class.isAssignableFrom(clazz) && !clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers()) && clazz != Command.class) {
                    result.add(clazz);
                }
            }
        }
        return result;
    }
}
