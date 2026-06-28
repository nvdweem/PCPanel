package com.getpcpanel.integration.homeassistant;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.integration.homeassistant.dto.HomeAssistantServer;
import com.getpcpanel.integration.homeassistant.dto.HomeAssistantServerStatus;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.SaveService.SaveEvent;
import com.getpcpanel.util.concurrent.Debouncer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * Owns the configured Home Assistant connections and is the single entry point the Home Assistant
 * commands use to talk to a server. Clients are rebuilt whenever the save file changes. A request
 * with no explicit server id resolves to the only configured server when there is exactly one,
 * which is what lets single-server setups skip server selection entirely.
 */
@Log4j2
@ApplicationScoped
public class HomeAssistantService {
    /** How long a ping result is reused before the server is probed again. */
    private static final long STATUS_TTL_MS = 5_000L;
    /** Short timeout for the UI status probe so an unreachable server can't pin a REST worker thread for
     *  the full 10s service-call timeout (and N dead servers don't add up to N*10s). */
    private static final java.time.Duration STATUS_PING_TIMEOUT = java.time.Duration.ofSeconds(2);

    @Inject SaveService saveService;
    @Inject ObjectMapper objectMapper;
    @Inject Debouncer debouncer;

    // serverId -> client, rebuilt on every save so url/token edits take effect immediately.
    private final Map<String, HomeAssistantClient> clients = new ConcurrentHashMap<>();
    private final Map<String, CachedStatus> statusCache = new ConcurrentHashMap<>();

    void onSave(@Observes SaveEvent event) {
        rebuild();
    }

    private synchronized void rebuild() {
        clients.clear();
        statusCache.clear();
        for (var server : servers()) {
            if (StringUtils.isNotBlank(server.id())) {
                clients.put(server.id(), new HomeAssistantClient(server, objectMapper));
            }
        }
    }

    public List<HomeAssistantServer> servers() {
        return saveService.get().getHomeAssistantServers();
    }

    /**
     * Resolves the client to use for a command. A blank/{@code null} id auto-selects the single
     * configured server (the common case); with multiple servers an explicit id is required.
     */
    @Nullable
    public HomeAssistantClient resolve(@Nullable String serverId) {
        if (clients.isEmpty()) {
            rebuild();
        }
        if (StringUtils.isBlank(serverId)) {
            return clients.size() == 1 ? clients.values().iterator().next() : null;
        }
        return clients.get(serverId);
    }

    /** Parse a pasted Home Assistant action YAML and perform it on the resolved server. */
    public boolean callAction(@Nullable String serverId, String actionYaml) {
        var client = resolve(serverId);
        if (client == null) {
            log.warn("Home Assistant: no server resolved for id '{}' (configured: {})", serverId, clients.size());
            return false;
        }
        var parsed = HaActionYaml.parse(actionYaml);
        if (parsed == null) {
            return false;
        }
        return client.callService(parsed.domain(), parsed.service(), parsed.data());
    }

    /**
     * High-frequency variant for analog sends: when the configured debounce window is &gt; 0, the call
     * is leading+trailing throttled per {@code key} (first move sent instantly, then at most one per
     * window, final value never dropped). With no window configured it sends immediately.
     */
    public void callActionThrottled(Object key, @Nullable String serverId, String actionYaml) {
        var window = debounceMs();
        if (window <= 0) {
            callAction(serverId, actionYaml);
            return;
        }
        debouncer.throttleLeading(key, () -> callAction(serverId, actionYaml), window, TimeUnit.MILLISECONDS);
    }

    private int debounceMs() {
        var ms = saveService.get().getHomeAssistantDebounceMs();
        return ms == null ? 0 : ms;
    }

    /** Per-server connection state for the UI, ping results cached briefly to stay cheap to poll. */
    public List<HomeAssistantServerStatus> serverStatuses() {
        var out = new ArrayList<HomeAssistantServerStatus>();
        for (var server : servers()) {
            out.add(new HomeAssistantServerStatus(server.id(), server.name(), server.url(), isConnected(server.id())));
        }
        return out;
    }

    /** True when any configured server is currently reachable. Drives the settings status dot. */
    public boolean isAnyConnected() {
        return servers().stream().anyMatch(s -> isConnected(s.id()));
    }

    public boolean isConnected(@Nullable String serverId) {
        var client = resolve(serverId);
        if (client == null) {
            return false;
        }
        var id = client.getServer().id();
        var cached = statusCache.get(id);
        var now = System.currentTimeMillis();
        if (cached != null && cached.expiry() > now) {
            return cached.connected();
        }
        var connected = client.ping(STATUS_PING_TIMEOUT);
        statusCache.put(id, new CachedStatus(connected, now + STATUS_TTL_MS));
        return connected;
    }

    private record CachedStatus(boolean connected, long expiry) {
    }
}
