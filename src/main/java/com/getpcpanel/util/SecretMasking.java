package com.getpcpanel.util;

import java.util.HashMap;
import java.util.List;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.integration.discord.dto.DiscordSettings;
import com.getpcpanel.integration.homeassistant.dto.HomeAssistantServer;
import com.getpcpanel.integration.mqtt.dto.MqttSettings;

/**
 * Makes stored secrets (OBS/MQTT passwords, MQTT username, Discord client secret, Home Assistant
 * long-lived tokens) <b>write-only</b> over the settings REST API. This is defence in depth: the API
 * is local-only, but the real secret bytes should still never be handed back to a browser.
 *
 * <p>The convention is a fixed sentinel {@link #MASK}:
 * <ul>
 *   <li><b>GET</b> ({@link #mask}) replaces a configured secret with {@link #MASK} so its real value
 *       never leaves the process; a blank/absent secret stays blank so the UI can still tell
 *       "not configured" apart from "configured".</li>
 *   <li><b>Save</b> ({@link #unmask}) keeps the currently-stored secret when the client sent nothing
 *       new — a blank value or the {@link #MASK} echoed back unchanged — and only accepts a genuinely
 *       new, non-blank, non-mask value. This is what stops a normal settings save (which round-trips
 *       the masked GET) from wiping the stored secret.</li>
 * </ul>
 */
public final class SecretMasking {
    /** Sentinel emitted by GET endpoints in place of a stored secret. Eight bullet characters — the UI
     *  renders a "configured" state for this and never treats it as a real secret. */
    public static final String MASK = "••••••••";

    private SecretMasking() {
    }

    /** GET side: hide a configured secret behind {@link #MASK}; leave a blank/absent secret untouched. */
    @Nullable
    public static String mask(@Nullable String stored) {
        return StringUtils.isBlank(stored) ? stored : MASK;
    }

    /** Save side: keep {@code stored} when the client sent nothing new (blank, or {@link #MASK} echoed
     *  back); otherwise accept {@code incoming}. Never persists the mask itself. */
    @Nullable
    public static String unmask(@Nullable String incoming, @Nullable String stored) {
        return StringUtils.isBlank(incoming) || MASK.equals(incoming) ? stored : incoming;
    }

    // ── MQTT (username + password) ────────────────────────────────────────────
    @Nullable
    public static MqttSettings mask(@Nullable MqttSettings s) {
        if (s == null) {
            return null;
        }
        return new MqttSettings(s.enabled(), s.host(), s.port(), mask(s.username()), mask(s.password()), s.secure(), s.baseTopic(), s.homeAssistant());
    }

    @Nullable
    public static MqttSettings unmask(@Nullable MqttSettings incoming, @Nullable MqttSettings stored) {
        if (incoming == null) {
            return stored;
        }
        return new MqttSettings(incoming.enabled(), incoming.host(), incoming.port(),
                unmask(incoming.username(), stored == null ? null : stored.username()),
                unmask(incoming.password(), stored == null ? null : stored.password()),
                incoming.secure(), incoming.baseTopic(), incoming.homeAssistant());
    }

    // ── Discord (client secret) ───────────────────────────────────────────────
    @Nullable
    public static DiscordSettings mask(@Nullable DiscordSettings s) {
        if (s == null) {
            return null;
        }
        return new DiscordSettings(s.enabled(), s.clientId(), mask(s.clientSecret()), s.redirectUri());
    }

    @Nullable
    public static DiscordSettings unmask(@Nullable DiscordSettings incoming, @Nullable DiscordSettings stored) {
        if (incoming == null) {
            return stored;
        }
        return new DiscordSettings(incoming.enabled(), incoming.clientId(),
                unmask(incoming.clientSecret(), stored == null ? null : stored.clientSecret()), incoming.redirectUri());
    }

    // ── Home Assistant servers (per-server token, matched by id) ───────────────
    @Nullable
    public static List<HomeAssistantServer> maskHaServers(@Nullable List<HomeAssistantServer> servers) {
        if (servers == null) {
            return null;
        }
        return servers.stream().map(s -> new HomeAssistantServer(s.id(), s.name(), s.url(), mask(s.token()))).toList();
    }

    @Nullable
    public static List<HomeAssistantServer> unmaskHaServers(@Nullable List<HomeAssistantServer> incoming, @Nullable List<HomeAssistantServer> stored) {
        if (incoming == null) {
            return stored;
        }
        var storedTokens = new HashMap<String, String>();
        if (stored != null) {
            for (var s : stored) {
                if (s.id() != null) {
                    storedTokens.put(s.id(), s.token());
                }
            }
        }
        return incoming.stream()
                       .map(s -> new HomeAssistantServer(s.id(), s.name(), s.url(), unmask(s.token(), storedTokens.get(s.id()))))
                       .toList();
    }
}
