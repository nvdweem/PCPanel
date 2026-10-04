package com.getpcpanel.integration.volume.platform.windows;

import com.getpcpanel.integration.volume.platform.AudioLevelMeter;
import com.getpcpanel.platform.WindowsBuild;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Audio-level lights' meter on Windows: a {@link CoreAudioMeterReader} owned by the one thread that samples it.
 */
@WindowsBuild
@ApplicationScoped
class WindowsAudioLevelMeter implements AudioLevelMeter {
    private final CoreAudioMeterReader reader = new CoreAudioMeterReader();

    @Override
    public boolean supported() {
        return reader.supported();
    }

    @Override
    public Levels sample() {
        return reader.sample();
    }

    /** A peak measured before a volume of {@code volumeDb}, as it comes out. */
    static float audible(float peak, float volumeDb, boolean muted) {
        return CoreAudioMeterReader.audible(peak, volumeDb, muted);
    }
}
