package com.getpcpanel.profile.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.AppLikeMapper;
import com.getpcpanel.StubBeans;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.integration.keyboard.command.CommandKeystroke;
import com.getpcpanel.profile.Save;

/**
 * Guards that a {@code profiles.json} written by an older release still loads, so upgrading does not
 * silently lose a user's configuration.
 *
 * <p>The fixtures under {@code src/test/resources/legacy-saves/} are not hand-written: each was
 * produced by the release it is named after, from the previous one, and verified to round-trip
 * through that release's own Jackson setup. Every property that version persisted carries a
 * distinctive non-default value, so a property that stops being read shows up as a lost value rather
 * than passing on a coincidental default. See the README next to them for the full provenance.
 *
 * <p>What breaks compatibility in practice is the {@code _type} discriminator on commands: 1.7.x/1.8
 * wrote fully-qualified class names ({@code JsonTypeInfo.Id.CLASS}), while this version writes the
 * short ids from {@code @JsonTypeName} and accepts the old names through
 * {@code @CommandMeta.legacyIds}. These tests exercise that path with real old files rather than
 * trusting the annotation to be present.
 */
@DisplayName("Older profiles.json files still load")
class LegacySaveCompatibilityTest {
    private static final String FIXTURES = "/legacy-saves/";
    /** The fixture written by this version — the one that gains new properties and commands. */
    private static final String CURRENT = "profiles-2.0.json";

    private final ObjectMapper mapper = AppLikeMapper.build();

    @BeforeAll
    static void stubContainer() {
        // Commands expose derived properties backed by CDI beans (see StubBeans); serializing a Save
        // outside the running app needs a container to resolve them.
        StubBeans.install();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "profiles-1.7.1.json", "profiles-1.8.json", CURRENT })
    @DisplayName("loads, and every command in it resolves to a concrete type")
    void loadsWithEveryCommandResolved(String fixture) throws IOException {
        var json = read(fixture);

        // Resolve the ids first: an unknown one makes the whole load throw, and the id it names is the
        // actionable part of the failure — reporting it here beats a stack trace from deep in a profile.
        for (var typeId : typeIdsIn(mapper.readTree(json))) {
            try {
                var command = mapper.readValue("{\"_type\":\"" + typeId + "\"}", Command.class);
                assertNotNull(command, () -> "_type '" + typeId + "' deserialized to null");
                assertInstanceOf(Command.class, command);
            } catch (Exception e) {
                fail("_type '" + typeId + "' from " + fixture + " no longer resolves, so every user upgrading from "
                        + "that version loses their configuration. A command was renamed, moved or removed without "
                        + "listing its old id in @CommandMeta.legacyIds: " + e.getMessage());
            }
        }

        var save = mapper.readValue(json, Save.class);
        assertEquals(3, save.getDevices().size(), () -> fixture + " should carry its three devices");
    }

    @Test
    @DisplayName("a 1.7.1 file keeps its values, not just its shape")
    void valuesSurviveFrom171() throws IOException {
        var save = mapper.readValue(read("profiles-1.7.1.json"), Save.class);

        assertEquals("obsAddress-8", save.getObsAddress());
        assertEquals(10L, save.getDblClickInterval());
        assertEquals("middleMiddle", save.getOverlayPosition().name());

        var device = save.getDeviceSave("9C6A1B2E3D4F");
        assertNotNull(device, "the pro device should still be there");
        assertEquals("pro-panel", device.getDisplayName());
        assertEquals(List.of("default", "gaming"), device.getProfiles().stream().map(p -> p.getName()).toList());

        var profile = device.getProfiles().get(0);
        assertEquals("activationShortcut-31", profile.getActivationShortcut());
        assertEquals(List.of("activateApplications-29", "activateApplications-30"), profile.getActivateApplications());
        assertEquals(36, profile.lightingConfig().getGlobalBrightness());

        var knob = profile.getKnobSettings().get(0);
        assertEquals(85, knob.getMinTrim());
        assertEquals(84, knob.getMaxTrim());
        assertTrue(knob.isLogarithmic());

        var keystroke = profile.getButtonData().values().stream()
                               .flatMap(c -> c.getCommands().stream())
                               .filter(CommandKeystroke.class::isInstance)
                               .map(CommandKeystroke.class::cast)
                               .findFirst()
                               .orElseThrow(() -> new AssertionError("the keystroke command was dropped"));
        assertEquals("keystroke-137", keystroke.getKeystroke());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "profiles-1.7.1.json", "profiles-1.8.json" })
    @DisplayName("re-saving an old file rewrites legacy ids and then stops changing")
    void resavingMigratesAndSettles(String fixture) throws IOException {
        var current = currentTypeIds();
        var storedIds = typeIdsIn(mapper.readTree(read(fixture)));
        assertFalse(storedIds.isEmpty(), () -> fixture + " has no commands to migrate");
        // Some commands deliberately kept their old fully-qualified name as their current @JsonTypeName,
        // so an overlap is expected; what matters is that the fixture still holds ids this version has
        // to translate. Without that it would be testing nothing.
        assertTrue(storedIds.stream().anyMatch(id -> !current.contains(id)),
                () -> "no id in " + fixture + " needs translating any more, so it guards nothing");

        var once = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readValue(read(fixture), Save.class));
        var rewritten = typeIdsIn(mapper.readTree(once));
        var stale = new TreeSet<>(rewritten);
        stale.removeAll(current);
        assertTrue(stale.isEmpty(), () -> "re-saving left ids that are not any command's @JsonTypeName: " + stale);
        assertEquals(storedIds.size(), rewritten.size(), "re-saving changed how many distinct commands the file holds");

        var twice = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readValue(once, Save.class));
        assertEquals(once, twice, "loading and re-saving is not idempotent, so every launch would rewrite the file");
    }

    @Test
    @DisplayName("a legacy file still arrives without provider identity, so the back-fill has work to do")
    void legacyFileHasNoProviderIdentity() throws IOException {
        var save = mapper.readValue(read("profiles-1.7.1.json"), Save.class);
        // The devices in a pre-2.0 file carry no provider identity at all. That absence is what
        // SaveService detects on load to back-fill "pcpanel" and write the .bak before rewriting the
        // file (the back-fill itself is covered by SaveServiceMigrationTest). If a default ever gets
        // added here the detection breaks silently, so pin the shape the migration relies on.
        assertTrue(save.getDevices().values().stream().allMatch(d -> d.getProviderId() == null),
                "a 1.7.1 file should deserialize with no providerId on any device");
        assertTrue(save.getDevices().values().stream().allMatch(d -> d.getCapabilities() == null),
                "a 1.7.1 file should deserialize with no capability descriptor on any device");
    }

    @Test
    @DisplayName("the current fixture covers every command, so a new one gets a compatibility record")
    void currentFixtureCoversEveryCommand() throws IOException {
        var present = typeIdsIn(mapper.readTree(read(CURRENT)));
        var missing = new TreeSet<String>();
        for (var command : SaveFixtureGenerator.concreteCommands()) {
            var id = command.getAnnotation(JsonTypeName.class);
            if (id != null && !present.contains(id.value())) {
                missing.add(command.getSimpleName() + " (" + id.value() + ")");
            }
        }
        assertTrue(missing.isEmpty(), () -> "Commands missing from " + CURRENT + ": " + missing
                + ". Regenerate it with SaveFixtureGenerator so the next release has an old file to load.");
    }

    @Test
    @DisplayName("the current fixture covers every persisted setting")
    void currentFixtureCoversEverySetting() throws IOException {
        var present = mapper.readTree(read(CURRENT));
        var missing = new TreeSet<String>();
        for (var field : Save.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            if (!present.has(field.getName())) {
                missing.add(field.getName());
            }
        }
        assertTrue(missing.isEmpty(), () -> "Save properties missing from " + CURRENT + ": " + missing
                + ". Regenerate it with SaveFixtureGenerator so the next release has an old file to load.");
    }

    /** Every distinct {@code _type} discriminator anywhere in the tree, wherever commands are nested. */
    private static Set<String> typeIdsIn(JsonNode node) {
        var found = new TreeSet<String>();
        collectTypeIds(node, found);
        return found;
    }

    private static void collectTypeIds(JsonNode node, Set<String> into) {
        if (node.isObject()) {
            var type = node.get("_type");
            if (type != null && type.isTextual()) {
                into.add(type.asText());
            }
        }
        node.forEach(child -> collectTypeIds(child, into));
    }

    /** The ids this version writes: one {@code @JsonTypeName} per assignable command. */
    private static Set<String> currentTypeIds() {
        var ids = new TreeSet<String>();
        for (var command : SaveFixtureGenerator.concreteCommands()) {
            var id = command.getAnnotation(JsonTypeName.class);
            if (id != null) {
                ids.add(id.value());
            }
        }
        return ids;
    }

    private static String read(String fixture) {
        try (var in = LegacySaveCompatibilityTest.class.getResourceAsStream(FIXTURES + fixture)) {
            if (in == null) {
                throw new IllegalStateException("fixture not on the classpath: " + FIXTURES + fixture);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read fixture " + fixture, e);
        }
    }
}
