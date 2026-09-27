package com.getpcpanel.commands.meta;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.command.Command;

/**
 * A command without a {@code FIELD_DEFS} entry makes {@code command-catalog.ts} throw at <em>module
 * load</em>, which breaks every page importing the catalog (device view, picker, settings, control):
 * the catalog assembles {@code COMMANDS} by mapping every backend command over its hand-written
 * {@code FIELD_DEFS} entry. {@code ng build} cannot catch it, because the failure is a runtime throw,
 * not a type error.
 *
 * <p>Scoped to commands with no {@code @CommandMeta.legacyIds}: those are keyed in {@code FIELD_DEFS}
 * by their literal {@code @JsonTypeName} id (e.g. {@code type: 'sonar.mute'}), which a plain substring
 * check can verify reliably. A legacy-keyed command's entry is instead built from a prefix constant
 * plus a class-name suffix (e.g. {@code WL + 'CommandWaveLinkChangeMute'}), which this check cannot
 * reconstruct without duplicating that convention. Every legacy-keyed command has its entry, and per
 * {@code docs/feature-module-structure.md} a new command gets a nice id, so this scope covers exactly
 * the case that recurs.
 */
@DisplayName("command-catalog.ts field-schema coverage for nice-id commands")
class CommandCatalogFieldSchemaCoverageTest {
    private static final Path CATALOG = Path.of("src/main/webui/src/app/features/commands/command-catalog.ts");

    @Test
    @DisplayName("FIELD_DEFS has an entry for every @CommandMeta command with no legacyIds")
    void everyNiceIdCommandHasAFieldSchema() throws Exception {
        var catalog = Files.readString(CATALOG, StandardCharsets.UTF_8);
        var missing = new TreeSet<String>();
        for (var id : niceCommandTypeIds()) {
            if (!catalog.contains("type: '" + id + "'")) {
                missing.add(id);
            }
        }
        assertTrue(missing.isEmpty(), () -> "command-catalog.ts's FIELD_DEFS is missing an entry (`type: '<id>'`) for: "
                + missing + ". Without one, importing the catalog throws at module load and breaks the whole UI.");
    }

    /** Every assignable command's {@code @JsonTypeName} id, for the commands with no legacy predecessor. */
    private static List<String> niceCommandTypeIds() throws Exception {
        var root = Path.of(Command.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var loader = CommandCatalogFieldSchemaCoverageTest.class.getClassLoader();
        var result = new ArrayList<String>();
        try (Stream<Path> walk = Files.walk(root.resolve("com").resolve("getpcpanel"))) {
            for (var classFile : walk.filter(p -> p.toString().endsWith(".class")).toList()) {
                var binary = root.relativize(classFile).toString();
                binary = binary.substring(0, binary.length() - ".class".length()).replace(java.io.File.separatorChar, '.');
                Class<?> c;
                try {
                    c = Class.forName(binary, false, loader);
                } catch (Throwable e) {
                    continue;
                }
                var meta = c.getAnnotation(CommandMeta.class);
                if (meta == null || meta.legacyIds().length > 0
                        || !Command.class.isAssignableFrom(c) || Modifier.isAbstract(c.getModifiers())) {
                    continue;
                }
                var typeName = c.getAnnotation(JsonTypeName.class);
                if (typeName != null && !typeName.value().isBlank()) {
                    result.add(typeName.value());
                }
            }
        }
        return result;
    }
}
