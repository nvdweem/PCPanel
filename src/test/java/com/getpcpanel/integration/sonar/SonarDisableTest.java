package com.getpcpanel.integration.sonar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.sonar.dto.SonarSettings;
import com.getpcpanel.profile.Save;
import com.getpcpanel.profile.SaveService.SaveEvent;

/** Switching the integration off drops it: no readiness, no connection reported, no writes. */
class SonarDisableTest {
    private static class RecordingClient extends SonarClient {
        final List<String> writes = new ArrayList<>();

        RecordingClient() {
            super(Path.of("no-such-coreProps.json"));
        }

        @Override public boolean setVolume(SonarRoute route, double value) {
            writes.add(route.volumePath(value));
            return true;
        }
    }

    private static SonarState connected() {
        return new SonarState(SonarMode.stream, Map.of(
                SonarRoute.of(SonarMode.stream, SonarMix.monitoring, SonarChannel.Game), new SonarLevel(0.5, false)));
    }

    private static SaveEvent saveWithSonar(boolean enabled) {
        var save = new Save();
        save.setSonar(new SonarSettings(enabled));
        return new SaveEvent(save, false);
    }

    @Test
    void switchingSonarOffDropsReadinessAndQueuedWrites() {
        var client = new RecordingClient();
        var changed = new SonarServiceFixtures.RecordingEvent();
        var service = SonarServiceFixtures.service(client, true, changed);
        service.replaceState(connected());
        service.setVolumeAt(SonarChannel.Game, SonarMix.monitoring, 0.9, 1_000);
        var firedBefore = changed.fired.get();

        service.onSaveChanged(saveWithSonar(false));
        service.flushDue(1_100);

        assertFalse(service.isReady());
        assertEquals(SonarState.UNKNOWN, service.snapshot());
        assertEquals(List.of(), client.writes);
        assertEquals(firedBefore + 1, changed.fired.get(), "the mute-colour layer must hear that Sonar is gone");
    }

    @Test
    void aSaveWithSonarOnKeepsItConnected() {
        var service = SonarServiceFixtures.service(new RecordingClient(), true);
        service.replaceState(connected());

        service.onSaveChanged(saveWithSonar(true));

        assertTrue(service.isReady());
    }

    @Test
    void aPollWhileSwitchedOffDropsReadiness() {
        var client = new RecordingClient();
        var service = SonarServiceFixtures.service(client, false);
        service.setInUse(true);
        service.replaceState(connected());
        service.setVolumeAt(SonarChannel.Game, SonarMix.monitoring, 0.9, 1_000);

        service.poll();
        service.flushDue(1_100);

        assertFalse(service.isReady());
        assertEquals(List.of(), client.writes);
    }
}
