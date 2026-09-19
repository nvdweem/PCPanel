package com.getpcpanel.integration.volume.platform.linux;

import java.io.File;
import java.util.Collection;

import javax.annotation.Nullable;

import jakarta.enterprise.event.Event;

import com.getpcpanel.integration.volume.platform.AudioSession;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import one.util.streamex.StreamEx;

@Getter
@EqualsAndHashCode(callSuper = true)
class PulseAudioAudioSession extends AudioSession {
    private final int index;

    /**
     * The stream's {@code pipewire.access.portal.app_id} (e.g. {@code com.spotify.Client}) when present.
     * Used as an extra match key so Flatpak apps - whose sink-input may carry no process metadata at all -
     * can still be targeted by binding to their app id (see #88, #92).
     */
    @EqualsAndHashCode.Exclude @Nullable private final String portalAppId;

    public PulseAudioAudioSession(Event<Object> eventBus, int index, int pid, File executable, String title, String icon, float volume, boolean muted, @Nullable String portalAppId) {
        super(eventBus, pid, executable, title, icon, volume, muted);
        this.index = index;
        this.portalAppId = portalAppId;
    }

    @Override
    protected AudioSession setVolumeNoTrigger(float volume) {
        return super.setVolumeNoTrigger(volume);
    }

    /**
     * A PulseAudio stream is additionally addressable by its portal app id, which for a sandboxed app may be the
     * only identifier it exposes.
     */
    @Override
    protected Collection<String> matchKeys() {
        return StreamEx.of(super.matchKeys()).append(portalAppId).toList();
    }
}
