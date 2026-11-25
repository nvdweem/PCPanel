package com.getpcpanel.integration.volume.platform.windows;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import org.apache.commons.io.FileUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.Json;
import com.getpcpanel.integration.volume.platform.AudioDeviceEvent;
import com.getpcpanel.integration.volume.platform.AudioReadyEvent;
import com.getpcpanel.integration.volume.platform.EventType;
import com.getpcpanel.platform.WindowsBuild;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.util.concurrent.Debouncer;
import com.getpcpanel.util.io.FileUtil;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.EntryStream;

/**
 * Keeps actions pointing at an audio device after Windows gives it a new endpoint id (see
 * {@link EndpointReplacements} for when that happens and how a replacement is recognised).
 *
 * <p>Every endpoint seen present is remembered in {@value #FILE}, with when it was first and last seen. Once the
 * audio backend is ready, and after each device change, the configuration's endpoint ids are checked: one that is
 * missing and clearly replaced is rewritten to its replacement's id throughout the configuration, as an undoable
 * change. An id the app never saw (configured before this existed) is described by Windows itself, which keeps a
 * replaced endpoint's properties. The same check pins advanced default device actions that hold a device name to
 * the one device it matches ({@link AdvancedDefaultDeviceNames}).
 */
@Log4j2
@ApplicationScoped
@WindowsBuild
public class EndpointRecovery {
    static final String FILE = "audio-endpoints.json";
    private static final TypeReference<Map<String, KnownEndpoint>> KNOWN_TYPE = new TypeReference<>() {
    };

    @Inject SndCtrlWindows sndCtrl;
    @Inject SaveService saveService;
    @Inject Json json;
    @Inject ObjectMapper mapper;
    @Inject FileUtil fileUtil;
    @Inject Debouncer debouncer;
    LongSupplier clock = System::currentTimeMillis;
    private volatile boolean ready;

    void onAudioReady(@Observes AudioReadyEvent event) {
        ready = true;
        schedule();
    }

    void onDeviceChange(@Observes AudioDeviceEvent event) {
        if (ready && event.eventType() != EventType.CHANGED) {
            schedule();
        }
    }

    /** Devices arrive in bursts (start-up, waking, a driver reinstall), so one check runs after the burst. */
    private void schedule() {
        debouncer.debounce(this, this::check, 2, TimeUnit.SECONDS);
    }

    synchronized void check() {
        try {
            var now = clock.getAsLong();
            var known = load();
            var before = Map.copyOf(known);
            var present = sndCtrl.getDevicesMap();
            present.forEach((id, device) -> {
                var interfaceName = device instanceof WindowsAudioDevice wad ? wad.interfaceName() : null;
                var previous = known.get(id);
                known.put(id, new KnownEndpoint(device.name(), interfaceName, device.dataflow(),
                        previous == null ? 0 : previous.firstSeen(), previous == null ? 0 : previous.lastSeen()).seen(now));
            });

            var referenced = EndpointReplacements.referencedIds(json.write(saveService.get()));
            for (var id : referenced) {
                if (!known.containsKey(id)) {
                    sndCtrl.describeEndpoint(id).ifPresent(endpoint -> known.put(id, endpoint));
                }
            }

            var replacements = EndpointReplacements.plan(referenced, known, present.keySet());
            if (!replacements.isEmpty()) {
                replacements.forEach((from, to) -> log.info("Audio device '{}' ({}) was replaced by Windows with '{}' ({}), updating the actions that use it",
                        known.get(from).name(), from, known.get(to).name(), to));
                if (saveService.replaceIds(replacements)) {
                    known.keySet().removeAll(replacements.keySet());
                }
            }
            if (!known.equals(before)) {
                write(known);
            }
            saveService.rewrite(save -> AdvancedDefaultDeviceNames.pin(save, present.values()));
        } catch (RuntimeException e) {
            log.warn("Unable to check the configured audio devices", e);
        }
    }

    private Map<String, KnownEndpoint> load() {
        var file = file();
        if (!file.isFile()) {
            return new HashMap<>();
        }
        try {
            return new HashMap<>(mapper.readValue(file, KNOWN_TYPE));
        } catch (IOException e) {
            log.warn("Unable to read {}, starting over", file, e);
            return new HashMap<>();
        }
    }

    private void write(Map<String, KnownEndpoint> known) {
        try {
            FileUtils.writeStringToFile(file(), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(EntryStream.of(known).toSortedMap()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Unable to write {}", file(), e);
        }
    }

    private File file() {
        return fileUtil.getFile(FILE);
    }
}
