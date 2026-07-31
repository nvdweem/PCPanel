package com.getpcpanel.report;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getpcpanel.AppLikeMapper;
import com.getpcpanel.integration.discord.dto.DiscordAuth;
import com.getpcpanel.integration.discord.dto.DiscordSettings;
import com.getpcpanel.integration.homeassistant.dto.HomeAssistantServer;
import com.getpcpanel.integration.mqtt.dto.MqttSettings;
import com.getpcpanel.profile.Save;

@DisplayName("ProfileRedactor (no credential leaves the machine in a bug report)")
class ProfileRedactionTest {
    private static final String SENTINEL = "SENTINEL-DO-NOT-PUBLISH";

    private static ProfileRedactor redactor() {
        var redactor = new ProfileRedactor();
        redactor.mapper = AppLikeMapper.build();
        return redactor;
    }

    private static Save saveWithCredentials() {
        var save = new Save();
        save.setObsPassword(SENTINEL);
        // The MQTT username is a credential here too — SecretMasking makes it write-only over the REST API.
        save.setMqtt(new MqttSettings(true, "broker", 1883, SENTINEL, SENTINEL, false, "buttonplus", MqttSettings.HomeAssistantSettings.DEFAULT));
        save.setDiscord(new DiscordSettings(true, "client-id", SENTINEL, DiscordSettings.DEFAULT_REDIRECT_URI));
        save.setDiscordAuth(new DiscordAuth(SENTINEL, SENTINEL, 1L, "scope", "user-id", "user-name"));
        save.setHomeAssistantServers(List.of(new HomeAssistantServer("id", "Home", "http://ha.local:8123", SENTINEL)));
        return save;
    }

    @Test
    @DisplayName("every configured credential is replaced, including inside nested records and lists")
    void redactsEveryConfiguredCredential() {
        var json = redactor().redactedJson(saveWithCredentials());

        assertFalse(json.contains(SENTINEL), () -> "a credential survived redaction:\n" + json);
        assertTrue(json.contains(ProfileRedactor.REDACTED), "the redaction marker should be present");
    }

    @Test
    @DisplayName("non-credential configuration is kept — it is what makes the report useful")
    void keepsNonCredentialConfiguration() {
        var json = redactor().redactedJson(saveWithCredentials());

        assertTrue(json.contains("broker"), "the MQTT host should survive");
        assertTrue(json.contains("http://ha.local:8123"), "the Home Assistant URL should survive");
        assertTrue(json.contains("client-id"), "the Discord client id is not a secret");
    }

    @Test
    @DisplayName("an unset credential stays empty, so 'never configured' stays distinguishable from 'hidden'")
    void leavesUnsetCredentialsAlone() {
        var save = new Save();
        save.setObsPassword("");

        var json = redactor().redactedJson(save);

        assertTrue(json.contains("\"obsPassword\" : \"\""), () -> "an empty credential should not be marked redacted:\n" + json);
    }

    /**
     * The guard that matters over time. A credential added to an integration later is only caught by
     * {@link ProfileRedactor} if its name reads like one, so every string reachable from the settings
     * side of {@link Save} must be classified here: either it is redacted by name, or it is listed
     * below as reviewed and harmless. A new field is neither, and fails this test until someone
     * decides which it is.
     */
    @Test
    @DisplayName("every string in the settings graph is either redacted or explicitly reviewed as harmless")
    void everyStringInTheSettingsGraphIsClassified() {
        var reachable = stringFieldsReachableFromSettings();
        var unclassified = new TreeSet<String>();
        for (var name : reachable) {
            if (!ProfileRedactor.isSecret(name) && !REVIEWED_NON_SECRET.contains(name)) {
                unclassified.add(name);
            }
        }

        assertTrue(unclassified.isEmpty(),
                () -> "Unclassified string(s) in the settings graph: " + unclassified
                        + ".\nIf a field is a credential, name it so ProfileRedactor.SECRET_FIELD matches it."
                        + "\nIf it is harmless, add it to REVIEWED_NON_SECRET in this test.");

        // An allowlist that outlives the field it excused would silently widen this guard's blind spot.
        var stale = new TreeSet<>(REVIEWED_NON_SECRET);
        stale.removeAll(reachable);
        assertTrue(stale.isEmpty(), () -> "REVIEWED_NON_SECRET names fields that no longer exist: " + stale);
    }

    /** Settings fields on {@link Save} that carry no credentials and whose graphs are large user config. */
    private static final Set<String> SKIPPED_SUBTREES = Set.of(
            "devices", "profiles", "curves", "focusVolumeOverrides", "discordSeenUsers");

    private static final Set<String> REVIEWED_NON_SECRET = Set.of(
            // Save — connection targets, local paths and overlay styling
            "obsAddress", "obsPort", "voicemeeterPath",
            "overlayBackgroundColor", "overlayBarBackgroundColor", "overlayBarColor", "overlayTextColor", "overlayFontFamily",
            // MqttSettings (+ its Home Assistant discovery block)
            "host", "baseTopic",
            // HomeAssistantServer
            "id", "name", "url",
            // DiscordSettings / DiscordAuth — identity, not authorisation
            "clientId", "redirectUri", "scope", "userId", "userName");

    /**
     * Walks the settings side of {@link Save} and returns the name of every string-valued field or
     * record component it reaches, descending into project types (including through lists).
     */
    private static Set<String> stringFieldsReachableFromSettings() {
        var found = new HashSet<String>();
        var seen = new HashSet<Class<?>>();
        var queue = new ArrayList<Class<?>>();

        for (var field : Save.class.getDeclaredFields()) {
            if (field.isSynthetic() || java.lang.reflect.Modifier.isStatic(field.getModifiers()) || SKIPPED_SUBTREES.contains(field.getName())) {
                continue;
            }
            if (field.getType() == String.class) {
                found.add(field.getName());
            }
            elementTypes(field.getGenericType()).forEach(queue::add);
        }

        while (!queue.isEmpty()) {
            var type = queue.remove(queue.size() - 1);
            if (!type.getName().startsWith("com.getpcpanel") || type.isEnum() || !seen.add(type)) {
                continue;
            }
            for (var field : type.getDeclaredFields()) {
                if (field.isSynthetic() || java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (field.getType() == String.class) {
                    found.add(field.getName());
                }
                elementTypes(field.getGenericType()).forEach(queue::add);
            }
        }
        return found;
    }

    /** The type itself, plus the type arguments of a generic container, so lists are followed. */
    private static List<Class<?>> elementTypes(java.lang.reflect.Type type) {
        if (type instanceof Class<?> clazz) {
            return List.of(clazz);
        }
        if (type instanceof ParameterizedType parameterized) {
            var result = new ArrayList<Class<?>>();
            for (var argument : parameterized.getActualTypeArguments()) {
                result.addAll(elementTypes(argument));
            }
            return result;
        }
        return List.of();
    }
}
