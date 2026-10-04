package com.getpcpanel.integration.volume.platform.windows;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import com.getpcpanel.integration.volume.platform.AudioSession;
import com.getpcpanel.integration.volume.platform.ISndCtrl;
import com.getpcpanel.integration.volume.platform.PlaybackGate;
import com.getpcpanel.platform.WindowsBuild;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

/**
 * Plays = an app with a session whose peak meter shows sound, on any output, read through its own
 * {@link CoreAudioMeterReader} (so on the visualizer's thread). Paused players keep their session but read silent, so
 * they don't count. An output or input has sound when its own endpoint meter shows it, read only when asked.
 */
@Log4j2
@WindowsBuild
@ApplicationScoped
class WindowsPlaybackGate implements PlaybackGate {
    /** Peaks below this are silence (about -50 dBFS). */
    static final float AUDIBLE = 0.003f;

    @Inject ISndCtrl sndCtrl;

    /** Re-reads the session list only when SndCtrl's list changes, or every 10 s: listing them is the costly part. */
    private final CoreAudioMeterReader reader = new CoreAudioMeterReader(10_000);
    private int sessionsSeen;
    private String lastLogged = "";

    private static boolean matches(AudioSession s, Collection<String> apps) {
        return apps.isEmpty() || StreamEx.of(apps).anyMatch(s::matches);
    }

    @Override
    public Playing check() {
        if (!reader.supported()) {
            return Playing.NOTHING;
        }
        var sessions = sndCtrl.getAllSessions();
        // Listing the sessions is the costly part, so only when SndCtrl's list changed (or every 10 s).
        var seen = StreamEx.of(sessions).mapToInt(s -> s.pid() * 31 + Objects.hashCode(s.deviceId())).sum() * 31 + sessions.size();
        if (seen != sessionsSeen) {
            sessionsSeen = seen;
            reader.invalidateSessions();
        }
        var candidates = StreamEx.of(sessions).filter(s -> !s.muted() && !s.isSystemSounds()).toList();
        reader.sampleProcesses(StreamEx.of(candidates).map(AudioSession::pid).toSet());
        var playing = playing(candidates, reader.sessionPeaks(), reader::endpointPeak);
        if (log.isDebugEnabled()) {
            var names = StreamEx.of(candidates).filter(s -> playing.any(List.of(s.executable().getName()))).map(AudioSession::title).joining(", ");
            if (!names.equals(lastLogged)) {
                lastLogged = names;
                log.debug("Playing (default output {}, loudest on {}): [{}]", sndCtrl.defaultPlayer(), playing.device(List.of()), names);
            }
        }
        return playing;
    }

    /** An endpoint's peak meter: {@code id} null is the default one; -1 when it can't be read. */
    interface EndpointMeter {
        float peak(@Nullable String id, boolean input);
    }

    /** {@link #playing(Collection, Map, EndpointMeter)} where no output or input has sound. */
    static Playing playing(Collection<AudioSession> candidates, Map<String, Float> peaks) {
        return playing(candidates, peaks, (id, input) -> 0);
    }

    /**
     * What plays, from the {@code candidates} (one session per app, as SndCtrl lists them) and the peaks of all their
     * sessions by {@code pid|deviceId}. An app can hold a session on every output and play on only one of them (games
     * do), so an app plays when any of its sessions is audible, and plays on the output where it is loudest. Outputs
     * and inputs are read from {@code endpoints} when asked, once each.
     */
    static Playing playing(Collection<AudioSession> candidates, Map<String, Float> peaks, EndpointMeter endpoints) {
        var loudest = new HashMap<Integer, Map.Entry<String, Float>>();
        for (var e : peaks.entrySet()) {
            if (e.getValue() <= AUDIBLE) {
                continue;
            }
            var bar = e.getKey().indexOf('|');
            var pid = Integer.parseInt(e.getKey().substring(0, bar));
            var current = loudest.get(pid);
            if (current == null || e.getValue() > current.getValue()) {
                loudest.put(pid, Map.entry(e.getKey().substring(bar + 1), e.getValue()));
            }
        }
        List<AudioSession> playing = StreamEx.of(candidates).filter(s -> loudest.containsKey(s.pid())).toList();
        var endpointSound = new HashMap<String, Boolean>();
        return new Playing() {
            @Override
            public boolean any(Collection<String> apps) {
                return StreamEx.of(playing).anyMatch(s -> matches(s, apps));
            }

            @Override
            public String device(Collection<String> apps) {
                return StreamEx.of(playing).filter(s -> matches(s, apps))
                               .map(s -> loudest.get(s.pid()))
                               .maxBy(Map.Entry::getValue)
                               .map(Map.Entry::getKey)
                               .orElse(null);
            }

            @Override
            public boolean output(@Nullable String deviceId) {
                return endpoint(deviceId, false);
            }

            @Override
            public boolean input(@Nullable String deviceId) {
                return endpoint(deviceId, true);
            }

            private boolean endpoint(@Nullable String deviceId, boolean input) {
                return endpointSound.computeIfAbsent((input ? "in|" : "out|") + deviceId, k -> endpoints.peak(deviceId, input) > AUDIBLE);
            }
        };
    }
}
