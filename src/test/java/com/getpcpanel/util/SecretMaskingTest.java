package com.getpcpanel.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.discord.dto.DiscordSettings;
import com.getpcpanel.integration.homeassistant.dto.HomeAssistantServer;
import com.getpcpanel.integration.mqtt.dto.MqttSettings;
import com.getpcpanel.profile.Save;
import com.getpcpanel.rest.model.dto.SettingsDto;

/**
 * Proves the settings secrets are write-only over REST: a GET never returns the real value, and a
 * save that echoes the mask back (a normal settings round-trip) keeps the stored secret instead of
 * wiping it.
 */
@DisplayName("Settings secret masking")
class SecretMaskingTest {
    private static final String OBS_SECRET = "obs-super-secret";
    private static final String MQTT_USER = "mqtt-user";
    private static final String MQTT_PASS = "mqtt-super-secret";
    private static final String HA_TOKEN = "ha-long-lived-token";
    private static final String DISCORD_SECRET = "discord-client-secret";

    private static Save saveWithSecrets() {
        var save = new Save();
        save.setObsPassword(OBS_SECRET);
        save.setMqtt(new MqttSettings(true, "broker", 1883, MQTT_USER, MQTT_PASS, false, "buttonplus", MqttSettings.HomeAssistantSettings.DEFAULT));
        save.setHomeAssistantServers(List.of(new HomeAssistantServer("id1", "Home", "http://ha.local:8123", HA_TOKEN)));
        save.setDiscord(new DiscordSettings(true, "client-id", DISCORD_SECRET, "http://localhost"));
        return save;
    }

    // ── (a) GET never returns the real secret ──────────────────────────────────
    @Test
    @DisplayName("GET masks every configured secret and leaves non-secret fields visible")
    void getMasksEverySecret() {
        var dto = SettingsDto.from(saveWithSecrets());

        assertEquals(SecretMasking.MASK, dto.getObsPassword());
        assertNotEquals(OBS_SECRET, dto.getObsPassword());
        assertEquals(SecretMasking.MASK, dto.getMqtt().username());
        assertNotEquals(MQTT_USER, dto.getMqtt().username());
        assertEquals(SecretMasking.MASK, dto.getMqtt().password());
        assertNotEquals(MQTT_PASS, dto.getMqtt().password());
        assertEquals(SecretMasking.MASK, dto.getHomeAssistantServers().getFirst().token());
        assertNotEquals(HA_TOKEN, dto.getHomeAssistantServers().getFirst().token());
        // Non-secret fields are still visible.
        assertEquals("http://ha.local:8123", dto.getHomeAssistantServers().getFirst().url());

        var discord = SecretMasking.mask(saveWithSecrets().getDiscord());
        assertEquals(SecretMasking.MASK, discord.clientSecret());
        assertNotEquals(DISCORD_SECRET, discord.clientSecret());
        assertEquals("client-id", discord.clientId()); // not a secret
    }

    @Test
    @DisplayName("a not-configured secret stays blank (not the mask) so the UI can tell them apart")
    void blankSecretsStayBlankSoUiCanTellNotConfigured() {
        var dto = SettingsDto.from(new Save()); // no secrets set
        assertNotEquals(SecretMasking.MASK, dto.getObsPassword());
        assertNotEquals(SecretMasking.MASK, dto.getMqtt().password());
    }

    // ── (b) a save echoing the mask keeps the stored secret ────────────────────
    @Test
    @DisplayName("a save echoing the masked secrets preserves the stored values")
    void saveWithMaskedSecretsPreservesStoredValues() {
        var save = saveWithSecrets();
        // Simulate the real round-trip: GET returns a masked DTO, the user edits nothing secret, PUT
        // sends it back and applyTo merges against the still-stored real secrets.
        var dto = SettingsDto.from(save);
        dto.applyTo(save);

        assertEquals(OBS_SECRET, save.getObsPassword());
        assertEquals(MQTT_USER, save.getMqtt().username());
        assertEquals(MQTT_PASS, save.getMqtt().password());
        assertEquals(HA_TOKEN, save.getHomeAssistantServers().getFirst().token());
    }

    @Test
    @DisplayName("a blank or masked value on save keeps the stored secret")
    void saveWithBlankSecretPreservesStoredValue() {
        assertEquals(OBS_SECRET, SecretMasking.unmask("", OBS_SECRET));
        assertEquals(OBS_SECRET, SecretMasking.unmask(null, OBS_SECRET));
        assertEquals(OBS_SECRET, SecretMasking.unmask(SecretMasking.MASK, OBS_SECRET));
    }

    // ── (c) a save with a genuinely new value updates it ───────────────────────
    @Test
    @DisplayName("a genuinely new value on save updates the secret")
    void saveWithNewValueUpdatesSecret() {
        var save = saveWithSecrets();
        var dto = SettingsDto.from(save);
        dto.setObsPassword("brand-new-obs-secret");
        dto.applyTo(save);
        assertEquals("brand-new-obs-secret", save.getObsPassword());

        assertEquals("new-value", SecretMasking.unmask("new-value", "old-value"));
    }

    @Test
    @DisplayName("MQTT merge updates only the credential the user actually changed")
    void mqttUnmaskUpdatesOnlyTheChangedCredential() {
        var stored = new MqttSettings(true, "broker", 1883, MQTT_USER, MQTT_PASS, false, "buttonplus", MqttSettings.HomeAssistantSettings.DEFAULT);
        // User typed a new password but left the (masked) username untouched.
        var incoming = new MqttSettings(true, "broker", 1883, SecretMasking.MASK, "new-mqtt-pass", false, "buttonplus", MqttSettings.HomeAssistantSettings.DEFAULT);
        var merged = SecretMasking.unmask(incoming, stored);
        assertEquals(MQTT_USER, merged.username());      // kept
        assertEquals("new-mqtt-pass", merged.password()); // updated
    }

    // ── Home Assistant: per-server token matched by id ─────────────────────────
    @Test
    @DisplayName("HA tokens merge by server id across add / edit / remove")
    void haServerTokensMergeByIdAcrossAddEditRemove() {
        var stored = List.of(
                new HomeAssistantServer("id1", "Home", "http://a", "token-a"),
                new HomeAssistantServer("id2", "Cabin", "http://b", "token-b"));

        var incoming = List.of(
                // existing server edited (name changed) but token left masked → keep token-a
                new HomeAssistantServer("id1", "Home Renamed", "http://a", SecretMasking.MASK),
                // brand-new server with a real token → accept it
                new HomeAssistantServer("id3", "Shed", "http://c", "token-c"));
        // id2 removed entirely.

        var merged = SecretMasking.unmaskHaServers(incoming, stored);
        assertEquals(2, merged.size());
        assertEquals("Home Renamed", merged.getFirst().name());
        assertEquals("token-a", merged.getFirst().token());
        assertEquals("token-c", merged.get(1).token());
    }

    @Test
    @DisplayName("masking leaves non-secret HA fields intact")
    void maskLeavesNonSecretHaFieldsIntact() {
        var masked = SecretMasking.maskHaServers(List.of(new HomeAssistantServer("id1", "Home", "http://a", HA_TOKEN)));
        assertEquals("id1", masked.getFirst().id());
        assertEquals("Home", masked.getFirst().name());
        assertEquals("http://a", masked.getFirst().url());
        assertEquals(SecretMasking.MASK, masked.getFirst().token());
    }
}
