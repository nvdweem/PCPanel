package com.getpcpanel.wavelink;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.util.FileUtil;

import dev.niels.wavelink.impl.model.WaveLinkChannel;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

/**
 * Remembers which applications Wave Link controls, so the focused-app volume dial can defer to Wave
 * Link for those apps instead of changing their OS volume.
 *
 * <p>This is deliberately <em>not</em> part of the user's saved settings — it is a transient cache
 * stored next to {@code profiles.json} in the data dir ({@code wavelink-controlled-apps.json}). The
 * point of persisting it is the startup race (#2): when the app launches while a Wave-Link-managed app
 * (e.g. a browser) already has focus, the focus-volume initial trigger fires before Wave Link has
 * connected and reported its channels, so the live membership isn't known yet. With the cache loaded
 * from disk the controlled-app set is available immediately, so that first trigger doesn't touch the
 * app's OS volume. While Wave Link is connected its live channels are authoritative; the cache is just
 * the fallback for the not-yet-connected window.
 *
 * <p>Entries are normalised to the lowercased executable name without directory or extension (e.g.
 * {@code firefox}) so the full focus-app path from {@code ISndCtrl.getFocusApplication()}
 * ({@code …\firefox.exe}) matches the Wave Link app id/name ({@code Firefox}), which carries no
 * extension. Apps whose Wave Link name differs from the executable (Wave Link's {@code Microsoft Edge}
 * vs {@code msedge.exe}) are bridged by {@link #learn} from a successful live correlation.
 */
@Log4j2
@ApplicationScoped
public class WaveLinkAppCache {
    private static final String FILE = "wavelink-controlled-apps.json";

    @Inject ObjectMapper mapper;
    @Inject FileUtil fileUtil;

    private final Set<String> controlled = ConcurrentHashMap.newKeySet();

    @PostConstruct
    void load() {
        try {
            var file = path();
            if (Files.exists(file)) {
                controlled.addAll(Arrays.asList(mapper.readValue(Files.readAllBytes(file), String[].class)));
                log.debug("Loaded {} Wave Link controlled-app entries", controlled.size());
            }
        } catch (Exception e) {
            log.debug("Could not load Wave Link app cache: {}", e.getMessage());
        }
    }

    /** Whether the OS-focused application (a full exe path) is one Wave Link is known to control. */
    public boolean isControlled(String focusAppPath) {
        var key = normalize(focusAppPath);
        return !key.isBlank() && controlled.contains(key);
    }

    /** Record an app — learned from a successful live correlation — as Wave-Link-controlled. */
    public void learn(String focusAppPath) {
        add(Set.of(normalize(focusAppPath)));
    }

    /** Refresh from the live channel membership (additive; never drops entries learned while connected). */
    public void syncFromChannels(Collection<WaveLinkChannel> channels) {
        add(StreamEx.of(channels)
                    .flatCollection(WaveLinkChannel::apps)
                    .flatMap(app -> StreamEx.of(app.id(), app.name()))
                    .map(WaveLinkAppCache::normalize)
                    .toSet());
    }

    private void add(Set<String> tokens) {
        // Don't mutate the caller's set (learn() passes an immutable Set.of); only persist on real growth.
        var fresh = StreamEx.of(tokens).remove(String::isBlank).remove(controlled::contains).toSet();
        if (fresh.isEmpty()) {
            return;
        }
        controlled.addAll(fresh);
        persist();
    }

    private synchronized void persist() {
        try {
            var file = path();
            Files.createDirectories(file.getParent());
            Files.write(file, mapper.writeValueAsBytes(new TreeSet<>(controlled)));
        } catch (Exception e) {
            log.debug("Could not persist Wave Link app cache: {}", e.getMessage());
        }
    }

    private Path path() {
        // Resolve against the configured data root (same as profiles.json) so the cache sits next to
        // the settings in every configuration — including dev (~/.pcpaneldev) and PCPANEL_ROOT overrides.
        return fileUtil.getFile(FILE).toPath();
    }

    /**
     * Lowercased executable name without directory or trailing extension, so a full focus-app path
     * ({@code C:\…\firefox.exe}) and a bare Wave Link app name ({@code Firefox}) compare equal.
     */
    static String normalize(String pathOrName) {
        if (pathOrName == null) {
            return "";
        }
        var s = pathOrName.trim().toLowerCase(Locale.ROOT);
        var slash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        if (slash >= 0) {
            s = s.substring(slash + 1);
        }
        var dot = s.lastIndexOf('.');
        if (dot > 0) { // keep leading-dot names intact; strip a real trailing extension (.exe, .app, …)
            s = s.substring(0, dot);
        }
        return s;
    }
}
