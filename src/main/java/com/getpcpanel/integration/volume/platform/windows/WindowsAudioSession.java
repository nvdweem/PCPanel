package com.getpcpanel.integration.volume.platform.windows;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import jakarta.enterprise.event.Event;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.getpcpanel.integration.volume.platform.AudioDevice;
import com.getpcpanel.integration.volume.platform.AudioSession;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode(callSuper = true)
class WindowsAudioSession extends AudioSession {
    @JsonIgnore @ToString.Exclude private final AudioDevice device;
    @JsonIgnore private final Set<Long> pointers = new HashSet<>();

    public WindowsAudioSession(AudioDevice device, Event<Object> eventBus, int pid, File executable, String title, String icon,
            float volume, boolean muted) {
        super(eventBus, pid, executable, title, icon, volume, muted);
        this.device = device;
    }

    /**
     * A session's volume, mute, name and icon changes are reported by {@code SndCtrl.dll} from the
     * threads the Windows audio API owns, and those callbacks may not block. Delivery goes through the
     * device's off-thread bus so no observer runs on one.
     */
    @Override
    protected void publish(Object event) {
        if (device instanceof WindowsAudioDevice windows) {
            windows.publish(event);
        } else {
            super.publish(event);
        }
    }
}
