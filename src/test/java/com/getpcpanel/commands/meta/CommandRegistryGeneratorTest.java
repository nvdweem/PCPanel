package com.getpcpanel.commands.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.command.Command;

/**
 * Generates the frontend command registry from the {@link CommandMeta} annotations and guards that the
 * committed {@code command-registry.generated.ts} stays current.
 *
 * <p>This is the build-time half of "the command registry is retrieved from Java": the authoritative
 * list of assignable commands and their picker metadata (label / category / kinds / integration / icon)
 * lives on each command in its own feature package, and the frontend consumes the generated file rather
 * than a hand-maintained catalog. Run with {@code -Dpcpanel.generate.catalog} to (re)write the file;
 * otherwise the test fails if the committed file is stale, telling you to regenerate.
 */
@DisplayName("Generated frontend command registry")
class CommandRegistryGeneratorTest {
    private static final Path OUTPUT = Path.of("src/main/webui/src/app/features/commands/command-registry.generated.ts");

    @Test
    @DisplayName("command-registry.generated.ts is up to date with @CommandMeta")
    void generatedRegistryIsCurrent() throws Exception {
        var expected = render(collect());
        if (System.getProperty("pcpanel.generate.catalog") != null) {
            Files.writeString(OUTPUT, expected);
            return;
        }
        var actual = Files.exists(OUTPUT) ? Files.readString(OUTPUT) : "";
        assertEquals(expected, actual,
                "command-registry.generated.ts is stale — regenerate with: ./mvnw test -Dtest=CommandRegistryGeneratorTest -Dpcpanel.generate.catalog");
    }

    /** Each assignable command: its persisted id (@JsonTypeName) + picker metadata (@CommandMeta). */
    private static List<Entry> collect() throws Exception {
        var root = Path.of(Command.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var loader = CommandRegistryGeneratorTest.class.getClassLoader();
        var result = new ArrayList<Entry>();
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
                if (meta == null) {
                    continue;
                }
                assertTrue(Command.class.isAssignableFrom(c) && !Modifier.isAbstract(c.getModifiers()),
                        () -> c.getName() + " has @CommandMeta but is not a concrete Command");
                var typeName = c.getAnnotation(JsonTypeName.class);
                assertTrue(typeName != null && !typeName.value().isBlank(),
                        () -> c.getName() + " has @CommandMeta but no @JsonTypeName id");
                result.add(new Entry(typeName.value(), meta));
            }
        }
        result.sort(Comparator.comparing(Entry::type));
        return result;
    }

    private static String render(List<Entry> entries) {
        var sb = new StringBuilder();
        sb.append("// GENERATED FILE — do not edit. Source: @CommandMeta on the command classes\n");
        sb.append("// (com.getpcpanel.**.command). Regenerate via CommandRegistryGeneratorTest.\n");
        sb.append("import { IconName } from '../../ui';\n");
        sb.append("import type { CommandCategory, CommandKind, Integration } from './command-catalog';\n\n");
        sb.append("export interface GeneratedCommand {\n");
        sb.append("  type: string;\n  label: string;\n  category: CommandCategory;\n");
        sb.append("  kinds: CommandKind[];\n  integration?: Integration;\n  icon: IconName;\n  /** previous _type id(s) for joining hand-written field schemas keyed by the old id */\n  legacy?: string;\n}\n\n");
        sb.append("export const GENERATED_COMMANDS: GeneratedCommand[] = [\n");
        for (var e : entries) {
            sb.append("  { type: ").append(q(e.type()));
            sb.append(", label: ").append(q(e.meta().label()));
            sb.append(", category: ").append(q(e.meta().category().name()));
            sb.append(", kinds: [");
            var ks = e.meta().kinds();
            for (var i = 0; i < ks.length; i++) {
                sb.append(q(ks[i].name()));
                if (i < ks.length - 1) {
                    sb.append(", ");
                }
            }
            sb.append("]");
            if (!e.meta().integration().isBlank()) {
                sb.append(", integration: ").append(q(e.meta().integration()));
            }
            sb.append(", icon: ").append(q(e.meta().icon()));
            if (e.meta().legacyIds().length > 0) {
                sb.append(", legacy: ").append(q(e.meta().legacyIds()[0]));
            }
            sb.append(" },\n");
        }
        sb.append("];\n");
        return sb.toString();
    }

    private static String q(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private record Entry(String type, CommandMeta meta) {
    }
}
