package com.getpcpanel.integration.sonar.command;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.getpcpanel.integration.sonar.SonarChannel;
import com.getpcpanel.integration.sonar.SonarMixSelection;
import com.getpcpanel.integration.sonar.SonarService;

/**
 * Hand-written recording stub for {@link SonarService}, served through {@code FakeCdi} exactly like
 * {@code CommandOscSendTest}'s {@code RecordingOscService} — {@code CommandSonar.getSonarService()}
 * resolves it via {@code CdiHelper.getBean}, so a command's real {@code execute} path runs with no
 * container.
 */
final class FakeSonarService extends SonarService {
    record VolumeCall(SonarChannel channel, SonarMixSelection mix, double value) {
    }

    record MuteCall(SonarChannel channel, SonarMixSelection mix, boolean muted) {
    }

    final List<VolumeCall> volumeCalls = new ArrayList<>();
    final List<MuteCall> muteCalls = new ArrayList<>();
    private boolean ready = true;
    @Nullable private Boolean currentMuted;

    FakeSonarService() {
        super(null, null, null);
    }

    void setReady(boolean ready) {
        this.ready = ready;
    }

    void setCurrentMuted(@Nullable Boolean muted) {
        currentMuted = muted;
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public void setVolume(SonarChannel channel, SonarMixSelection mix, double value) {
        volumeCalls.add(new VolumeCall(channel, mix, value));
    }

    @Override
    public void setMute(SonarChannel channel, SonarMixSelection mix, boolean muted) {
        muteCalls.add(new MuteCall(channel, mix, muted));
    }

    @Nullable
    @Override
    public Boolean mutedOrNull(SonarChannel channel, SonarMixSelection mix) {
        return currentMuted;
    }
}
