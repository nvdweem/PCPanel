package com.getpcpanel.integration.volume.platform.linux;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import com.getpcpanel.integration.volume.platform.ISndCtrl;
import com.getpcpanel.integration.volume.platform.PlaybackGate;
import com.getpcpanel.platform.LinuxBuild;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import one.util.streamex.StreamEx;

/**
 * Plays = a stream that isn't paused (corked) or muted, from the sessions pactl already keeps up to date. No
 * recording is needed to tell, so watching costs nothing. An app that keeps an unpaused stream while its player is
 * paused counts as playing.
 *
 * <p>An output has sound while such a stream plays on it. An input has no level without recording it, so it counts as
 * having sound while it exists and isn't muted; the visualizer leaves an input whose recording stays silent alone for a
 * while, so the sources after it still get their turn.
 */
@LinuxBuild
@ApplicationScoped
class LinuxPlaybackGate implements PlaybackGate {
    @Inject ISndCtrl sndCtrl;

    private static boolean matches(PulseAudioAudioSession s, Collection<String> apps) {
        return apps.isEmpty() || StreamEx.of(apps).anyMatch(s::matches);
    }

    @Override
    public Playing check() {
        List<PulseAudioAudioSession> playing = StreamEx.of(sndCtrl.getAllSessions())
                                                       .select(PulseAudioAudioSession.class)
                                                       .filter(s -> !s.corked() && !s.muted() && !s.isSystemSounds())
                                                       .toList();
        return playing(playing, sndCtrl);
    }

    /** What plays, from the un-paused, unmuted streams; outputs and inputs are looked up in {@code sndCtrl}. */
    static Playing playing(List<PulseAudioAudioSession> playing, ISndCtrl sndCtrl) {
        return new Playing() {
            @Override
            public boolean any(Collection<String> apps) {
                return StreamEx.of(playing).anyMatch(s -> matches(s, apps));
            }

            @Override
            public String device(Collection<String> apps) {
                // No levels without recording: the first matching stream's output.
                return StreamEx.of(playing).filter(s -> matches(s, apps)).map(PulseAudioAudioSession::deviceId).nonNull().findFirst().orElse(null);
            }

            @Override
            public boolean output(@Nullable String deviceId) {
                var id = deviceId == null ? sndCtrl.defaultPlayer() : deviceId;
                // With the default output unknown, any stream counts.
                return StreamEx.of(playing).anyMatch(s -> id == null || Objects.equals(id, s.deviceId()));
            }

            @Override
            public boolean input(@Nullable String deviceId) {
                var id = deviceId == null ? sndCtrl.defaultRecorder() : deviceId;
                var device = id == null ? null : sndCtrl.getDevice(id);
                return device != null && device.isInput() && !device.muted();
            }
        };
    }
}
