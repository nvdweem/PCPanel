package com.getpcpanel.graalvm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The GraalVM native-image build args live in TWO places that must stay identical: the
 * {@code quarkus.native.additional-build-args} property in {@code pom.xml} (used when
 * {@code quarkus:dev}/{@code quarkus:build} is invoked directly) and the copy in
 * {@code application.properties} (authoritative during a full {@code mvn package}/CI). They are
 * hand-maintained, so this test fails the build the moment they drift, rather than silently
 * producing different native images locally vs. in CI (see CLAUDE.md, GraalVM native-image section).
 *
 * <p>The per-OS {@code ${native.awt.args}} / {@code ${native.platform.linker.args}} placeholders are
 * identical Maven references in both files, so they are stripped before comparison.
 */
@DisplayName("Native-image additional-build-args parity (pom.xml vs application.properties)")
class NativeBuildArgsParityTest {
    private static final String KEY = "quarkus.native.additional-build-args";
    private static final Pattern POM_ELEMENT =
            Pattern.compile("<" + Pattern.quote(KEY) + ">(.*?)</" + Pattern.quote(KEY) + ">", Pattern.DOTALL);

    @Test
    @DisplayName("pom.xml and application.properties declare the same set of build args")
    void argsAreInSync() {
        var root = projectRoot();
        var pomArgs = parse(extractFromPom(root.resolve("pom.xml")));
        var propArgs = parse(extractFromProperties(root.resolve("src/main/resources/application.properties")));

        // Guard against an extraction bug silently comparing two empty sets.
        assertTrue(pomArgs.size() > 15, "Suspiciously few args parsed from pom.xml: " + pomArgs);

        var onlyInPom = new TreeSet<>(pomArgs);
        onlyInPom.removeAll(propArgs);
        var onlyInProps = new TreeSet<>(propArgs);
        onlyInProps.removeAll(pomArgs);

        assertEquals(propArgs, pomArgs,
                "quarkus.native.additional-build-args drifted between pom.xml and application.properties.\n"
                        + "  Only in pom.xml: " + onlyInPom + "\n"
                        + "  Only in application.properties: " + onlyInProps + "\n"
                        + "Keep both copies identical (see CLAUDE.md, GraalVM native-image section).");
    }

    private static Set<String> parse(String raw) {
        var withoutPlaceholders = raw
                .replace("${native.awt.args}", "")
                .replace("${native.platform.linker.args}", "");
        return Arrays.stream(withoutPlaceholders.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static String extractFromPom(Path pom) {
        var matcher = POM_ELEMENT.matcher(read(pom));
        assertTrue(matcher.find(), "Could not find <" + KEY + "> element in " + pom);
        return matcher.group(1);
    }

    private static String extractFromProperties(Path propsFile) {
        var props = new Properties();
        try (var in = Files.newInputStream(propsFile)) {
            props.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        var value = props.getProperty(KEY);
        assertNotNull(value, "Could not find " + KEY + " in " + propsFile);
        return value;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path projectRoot() {
        var start = Path.of("").toAbsolutePath();
        for (var dir = start; dir != null; dir = dir.getParent()) {
            if (Files.exists(dir.resolve("pom.xml"))
                    && Files.exists(dir.resolve("src/main/resources/application.properties"))) {
                return dir;
            }
        }
        throw new IllegalStateException("Could not locate project root from " + start);
    }
}
