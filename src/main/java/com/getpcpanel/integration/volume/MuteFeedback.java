package com.getpcpanel.integration.volume;

import java.util.Collection;

import javax.annotation.Nullable;

import com.getpcpanel.integration.volume.platform.AudioDevice;
import com.getpcpanel.integration.volume.platform.AudioSession;
import com.getpcpanel.integration.volume.platform.MuteType;

import one.util.streamex.StreamEx;

/**
 * The overlay text of a mute button, worked out before the mute runs: the target's state then, put through the
 * {@link MuteType}, is the state the mute leaves it in.
 */
public final class MuteFeedback {
    private MuteFeedback() {
    }

    public static String text(String name, boolean muted) {
        return name + " · " + (muted ? "Muted" : "Unmuted");
    }

    /** For an app mute: the first session one of {@code names} names, under its friendly name; null when none plays. */
    @Nullable
    public static String forSessions(Collection<AudioSession> sessions, Collection<String> names, MuteType type) {
        return StreamEx.of(sessions)
                       .findFirst(s -> names.stream().anyMatch(s::matches)
                               || (s.executable() != null && AppOutputRouter.namesApp(names, s.executable().getPath())))
                       .map(s -> text(s.title(), type.convert(s.muted())))
                       .orElse(null);
    }

    /** For a device mute: the device under its name; null when the device is unknown. */
    @Nullable
    public static String forDevice(@Nullable AudioDevice device, MuteType type) {
        return device == null ? null : text(device.name(), type.convert(device.muted()));
    }
}
