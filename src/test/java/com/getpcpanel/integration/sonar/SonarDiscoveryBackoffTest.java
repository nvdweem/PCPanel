package com.getpcpanel.integration.sonar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.sonar.dto.SonarSettings;
import com.getpcpanel.profile.Save;
import com.getpcpanel.profile.SaveService;

import re.walk.sonar.SonarClient;
import re.walk.sonar.model.SonarMode;
import re.walk.sonar.model.SonarState;

/**
 * While Sonar cannot be found the poll looks for it at a growing interval (2 s, 4 s, … capped at 10 s);
 * once found it reads every tick. Using a Sonar control clears the wait.
 */
class SonarDiscoveryBackoffTest {
    /** A GG that is closed until {@link #available} is set; each failed lookup costs two mode reads (read + re-resolve). */
    private static final class SwitchableClient extends SonarClient {
        final AtomicInteger modeReads = new AtomicInteger();
        volatile boolean available;

        SwitchableClient() {
            super(Path.of("no-such-coreProps.json"));
        }

        @Override
        public Optional<SonarMode> fetchMode() {
            modeReads.incrementAndGet();
            return available ? Optional.of(SonarMode.stream) : Optional.empty();
        }

        @Override
        public Optional<SonarState> fetchState(SonarMode mode) {
            return Optional.of(new SonarState(mode, Map.of()));
        }
    }

    @Test
    void aMissingSonarIsLookedForAtAGrowingInterval() {
        var client = new SwitchableClient();
        var service = SonarServiceFixtures.service(client, true);
        var t = 100_000L;

        service.poll(t);                    // not found: next look after 2 s
        assertEquals(2, client.modeReads.get());
        service.poll(t + 1_000);
        assertEquals(2, client.modeReads.get(), "inside the backoff nothing is sent");
        service.poll(t + 2_000);            // not found again: next look after 4 s
        assertEquals(4, client.modeReads.get());
        service.poll(t + 5_000);
        assertEquals(4, client.modeReads.get());
        service.poll(t + 6_000);
        assertEquals(6, client.modeReads.get());
    }

    @Test
    void aFoundSonarIsReadEveryTick() {
        var client = new SwitchableClient();
        client.available = true;
        var service = SonarServiceFixtures.service(client, true);

        service.poll(100_000);
        service.poll(101_000);
        service.poll(102_000);

        assertEquals(3, client.modeReads.get());
        assertTrue(service.isReady());
    }

    @Test
    void usingASonarControlLooksForSonarOnTheNextTick() throws InterruptedException {
        var client = new SwitchableClient();
        var service = SonarServiceFixtures.service(client, true);
        var t = System.currentTimeMillis();
        service.poll(t);
        service.poll(t + 2_000);            // two misses: the next look would wait 4 s
        client.available = true;

        service.onUsed();
        // The use clears the backoff on its own thread; the next one-second tick then finds Sonar.
        var deadline = System.currentTimeMillis() + 5_000;
        while (!service.isReady() && System.currentTimeMillis() < deadline) {
            service.poll(t + 3_000);
            Thread.sleep(10);
        }

        assertTrue(service.isReady(), "a tick right after the use should have found Sonar");
    }

    @Test
    void switchingSonarBackOnLooksForItAtOnce() {
        var client = new SwitchableClient();
        var save = new Save();
        save.setSonar(new SonarSettings(true, SonarSettings.DEFAULT_UPDATES_PER_SECOND));
        var service = new SonarService(client, new SaveService() {
            @Override public Save get() {
                return save;
            }
        }, null, null);
        var t = 100_000L;
        service.poll(t);                    // a miss starts the backoff
        save.setSonar(new SonarSettings(false, SonarSettings.DEFAULT_UPDATES_PER_SECOND));
        service.poll(t + 500);              // switched off: nothing to find, the backoff is cleared
        save.setSonar(new SonarSettings(true, SonarSettings.DEFAULT_UPDATES_PER_SECOND));
        var readsBefore = client.modeReads.get();

        service.poll(t + 1_000);            // still inside the first backoff step had it not been cleared

        assertEquals(readsBefore + 2, client.modeReads.get());
    }
}
