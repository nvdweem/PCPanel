package com.getpcpanel.commands.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.commands.CommandModule;

/**
 * Guards the <b>decentralized</b> command type registry. There is intentionally no central
 * {@code @JsonSubTypes} list: every concrete {@link Command} declares its own stable id with
 * {@code @JsonTypeName} in its own package, and every feature module lists its own commands via the
 * {@link CommandModule} CDI SPI. These tests enforce the invariants that keep that safe, so a new
 * command/plugin that forgets either half fails the build rather than silently breaking at runtime:
 *
 * <ol>
 *   <li><b>Self-identifying</b> — every concrete command carries a unique, non-blank {@code @JsonTypeName}.</li>
 *   <li><b>Module-covered</b> — the union of all {@link CommandModule#commandTypes()} equals exactly the
 *       set of concrete commands (none missing, none stale/duplicated).</li>
 *   <li><b>Resolvable</b> — once registered, every id deserializes back to its class (the save-migration
 *       guard: a moved class still loads under its frozen id).</li>
 * </ol>
 */
@DisplayName("Decentralized Command type registry")
class CommandSubtypeRegistryTest {
    private final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    @DisplayName("every concrete command has a unique @JsonTypeName")
    void everyCommandSelfIdentifies() throws Exception {
        var seen = new HashSet<String>();
        for (var c : findConcreteCommandSubtypes()) {
            var ann = c.getAnnotation(JsonTypeName.class);
            assertTrue(ann != null && !ann.value().isBlank(), () -> c.getName() + " is missing a @JsonTypeName id");
            assertTrue(seen.add(ann.value()), () -> "Duplicate @JsonTypeName id: " + ann.value());
        }
    }

    @Test
    @DisplayName("the CommandModule SPI covers exactly the concrete commands (none missing, none stale)")
    void everyCommandIsCoveredByExactlyOneModule() throws Exception {
        var concrete = findConcreteCommandSubtypes();
        var declared = new ArrayList<Class<?>>();
        for (var module : findCommandModules()) {
            declared.addAll(module.commandTypes());
        }
        var declaredSet = new HashSet<>(declared);

        var missing = concrete.stream().filter(c -> !declaredSet.contains(c)).map(Class::getName).sorted().toList();
        assertTrue(missing.isEmpty(), "Concrete commands not listed in any CommandModule (add them to the feature's module): " + missing);

        var stale = declaredSet.stream().filter(c -> !concrete.contains(c)).map(Class::getName).sorted().toList();
        assertTrue(stale.isEmpty(), "CommandModule lists a type that is not a concrete command: " + stale);

        assertEquals(declared.size(), declaredSet.size(), "A command is listed by more than one CommandModule");
    }

    @Test
    @DisplayName("every command id deserializes back to its class once registered")
    void idsResolveAfterRegistration() throws Exception {
        var concrete = findConcreteCommandSubtypes();
        concrete.forEach(mapper::registerSubtypes);
        for (var c : concrete) {
            var name = c.getAnnotation(JsonTypeName.class).value();
            try {
                assertInstanceOf(c, mapper.readValue("{\"_type\":\"" + name + "\"}", Command.class),
                        () -> "id '" + name + "' resolved to the wrong class");
            } catch (Exception e) {
                fail("id '" + name + "' (for " + c.getName() + ") no longer deserializes: " + e.getMessage());
            }
        }
        assertFalse(concrete.isEmpty(), "no commands discovered — scan is broken");
    }

    @SuppressWarnings("unchecked")
    private static List<CommandModule> findCommandModules() throws Exception {
        var result = new ArrayList<CommandModule>();
        for (var c : scan(c -> CommandModule.class.isAssignableFrom(c) && !c.isInterface() && !Modifier.isAbstract(c.getModifiers()))) {
            result.add((CommandModule) c.getDeclaredConstructor().newInstance());
        }
        return result;
    }

    private static Set<Class<?>> findConcreteCommandSubtypes() throws Exception {
        return scan(c -> Command.class.isAssignableFrom(c) && !c.isInterface() && !Modifier.isAbstract(c.getModifiers()) && c != Command.class);
    }

    private static Set<Class<?>> scan(java.util.function.Predicate<Class<?>> keep) throws Exception {
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
                if (keep.test(clazz)) {
                    result.add(clazz);
                }
            }
        }
        return result;
    }
}
