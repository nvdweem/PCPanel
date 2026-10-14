package com.getpcpanel.integration.volume.platform.osx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.volume.platform.MuteType;

/**
 * The tap bookkeeping of {@link OsxProcessTapService} against a fake CoreAudio: taps follow the default output,
 * keep their volume and mute, and recover from a tap that cannot be created. Runs on every OS.
 */
class OsxProcessTapServiceTest {
    private static final int SPEAKERS = 1;
    private static final int HEADPHONES = 2;
    private static final int MONITOR = 3;
    private static final int FIREFOX = 101;
    private static final int MUSIC = 202;

    private FakeCoreAudio coreAudio;
    private FakeTaps factory;
    private Set<Integer> alive;
    private OsxProcessTapService service;

    @BeforeEach
    void setUp() {
        coreAudio = new FakeCoreAudio();
        factory = new FakeTaps();
        alive = ConcurrentHashMap.newKeySet();
        alive.addAll(List.of(FIREFOX, MUSIC));
        service = new OsxProcessTapService(coreAudio, factory, alive::contains, true, Duration.ofMillis(20));
    }

    @AfterEach
    void tearDown() {
        factory.release();
        service.destroy();
    }

    @Test
    void movesEveryTapToTheNewDefaultOutputKeepingVolumeAndMute() {
        service.setVolume(FIREFOX, 0.3f);
        service.setVolume(MUSIC, 0.6f);
        service.mute(MUSIC, MuteType.mute);

        coreAudio.defaultOutput = HEADPHONES;
        service.migrateToDefaultOutput();

        var firefox = factory.live(FIREFOX);
        assertEquals(uid(HEADPHONES), firefox.uid);
        assertEquals(0.3f, firefox.gain);
        assertFalse(firefox.muted);
        var music = factory.live(MUSIC);
        assertEquals(uid(HEADPHONES), music.uid);
        assertEquals(0.6f, music.gain);
        assertTrue(music.muted);
        assertEquals(2, factory.liveCount(), "the taps on the speakers are closed");
    }

    @Test
    void leavesTapsAloneWhileTheDefaultOutputStays() {
        service.setVolume(FIREFOX, 0.3f);

        service.migrateToDefaultOutput();

        assertEquals(1, factory.created.size());
        assertEquals(uid(SPEAKERS), factory.live(FIREFOX).uid);
    }

    @Test
    void keepsTapsWhileTheDefaultOutputCannotBeRead() {
        service.setVolume(FIREFOX, 0.3f);

        coreAudio.defaultOutput = 0;
        service.migrateToDefaultOutput();

        assertEquals(1, factory.created.size());
        assertEquals(uid(SPEAKERS), factory.live(FIREFOX).uid);
    }

    @Test
    void notificationMovesTapsOffTheNotifyingThread() throws InterruptedException {
        service.setVolume(FIREFOX, 0.3f);
        factory.block();

        coreAudio.defaultOutput = HEADPHONES;
        service.defaultOutputChanged();

        assertTrue(factory.entered.await(5, TimeUnit.SECONDS), "the move started");
        assertNotEquals(Thread.currentThread(), factory.blockedThread, "the move runs on its own thread");
        factory.release();
        await(() -> factory.liveOn(FIREFOX, HEADPHONES));
        assertEquals(0.3f, factory.live(FIREFOX).gain);
    }

    @Test
    void rapidSwitchingEndsOnTheLastOutputWithoutLeakingTaps() throws InterruptedException {
        service.setVolume(FIREFOX, 0.3f);
        service.setVolume(MUSIC, 0.6f);
        factory.block();

        coreAudio.defaultOutput = HEADPHONES;
        service.defaultOutputChanged();
        assertTrue(factory.entered.await(5, TimeUnit.SECONDS), "the first move started");
        for (var output : List.of(MONITOR, SPEAKERS, HEADPHONES, MONITOR)) {
            coreAudio.defaultOutput = output;
            service.defaultOutputChanged();
        }
        factory.release();

        await(() -> factory.liveOn(FIREFOX, MONITOR) && factory.liveOn(MUSIC, MONITOR));
        assertEquals(2, factory.liveCount(), "one tap per app, every other one closed");
        // Initial taps, the move in progress (up to both apps) and a single follow-up for all changes queued meanwhile.
        assertTrue(factory.created.size() <= 6, "changes during a move are coalesced, created " + factory.created.size());
    }

    @Test
    void retriesATapThatCouldNotBeMovedKeepingVolumeAndMute() {
        service.setVolume(MUSIC, 0.6f);
        service.mute(MUSIC, MuteType.mute);
        factory.failingUids.add(uid(HEADPHONES));

        coreAudio.defaultOutput = HEADPHONES;
        service.defaultOutputChanged();
        await(() -> factory.created.size() > 1 || factory.failures.get() > 0);
        assertEquals(0, factory.liveCount(), "the tap on the old output is gone");

        factory.failingUids.clear();
        await(() -> factory.liveOn(MUSIC, HEADPHONES));
        assertEquals(0.6f, factory.live(MUSIC).gain);
        assertTrue(factory.live(MUSIC).muted);
    }

    @Test
    void stopsRetryingAfterAFewAttemptsUntilTheNextChange() throws InterruptedException {
        service.setVolume(FIREFOX, 0.3f);
        factory.failingUids.add(uid(HEADPHONES));

        coreAudio.defaultOutput = HEADPHONES;
        service.defaultOutputChanged();
        await(() -> factory.failures.get() == 1 + OsxProcessTapService.MAX_RETRIES);
        Thread.sleep(300);
        assertEquals(1 + OsxProcessTapService.MAX_RETRIES, factory.failures.get(), "retries are bounded");

        factory.failingUids.clear();
        coreAudio.defaultOutput = MONITOR;
        service.defaultOutputChanged();
        await(() -> factory.liveOn(FIREFOX, MONITOR));
        assertEquals(0.3f, factory.live(FIREFOX).gain);
    }

    @Test
    void usingTheControlRecoversATapThatCouldNotBeMoved() {
        service.setVolume(FIREFOX, 0.3f);
        service.mute(FIREFOX, MuteType.mute);
        factory.failingUids.add(uid(HEADPHONES));
        coreAudio.defaultOutput = HEADPHONES;
        service.migrateToDefaultOutput();
        assertEquals(0, factory.liveCount());

        factory.failingUids.clear();
        service.setVolume(FIREFOX, 0.5f);

        var tap = factory.live(FIREFOX);
        assertEquals(uid(HEADPHONES), tap.uid);
        assertEquals(0.5f, tap.gain);
        assertTrue(tap.muted, "the mute survived the failed move");
    }

    @Test
    void fullVolumeForgetsATapThatCouldNotBeMoved() {
        service.setVolume(FIREFOX, 0.3f);
        factory.failingUids.add(uid(HEADPHONES));
        coreAudio.defaultOutput = HEADPHONES;
        service.migrateToDefaultOutput();

        service.setVolume(FIREFOX, 1);
        factory.failingUids.clear();
        coreAudio.defaultOutput = MONITOR;
        service.migrateToDefaultOutput();

        assertEquals(0, factory.liveCount());
        assertEquals(1, factory.created.size(), "nothing is tapped again");
    }

    @Test
    void dropsTapsOfProcessesThatEnded() {
        service.setVolume(FIREFOX, 0.3f);
        service.setVolume(MUSIC, 0.6f);
        alive.remove(MUSIC);

        coreAudio.defaultOutput = HEADPHONES;
        service.migrateToDefaultOutput();

        assertEquals(1, factory.liveCount());
        assertTrue(factory.liveOn(FIREFOX, HEADPHONES));
    }

    @Test
    void volumeAndMuteFollowTheNewOutputWithoutANotification() {
        service.setVolume(FIREFOX, 0.3f);
        coreAudio.defaultOutput = HEADPHONES;

        service.setVolume(FIREFOX, 0.4f);

        assertTrue(factory.liveOn(FIREFOX, HEADPHONES));
        assertEquals(0.4f, factory.live(FIREFOX).gain);
        assertEquals(1, factory.liveCount());
    }

    @Test
    void doesNothingWhereProcessTapsAreUnsupported() throws InterruptedException {
        var unsupported = new OsxProcessTapService(coreAudio, factory, alive::contains, false, Duration.ofMillis(20));
        try {
            unsupported.setVolume(FIREFOX, 0.3f);
            coreAudio.defaultOutput = HEADPHONES;
            unsupported.defaultOutputChanged();
            Thread.sleep(100);
            assertTrue(factory.created.isEmpty());
        } finally {
            unsupported.destroy();
        }
    }

    private static String uid(int deviceId) {
        return "device-" + deviceId;
    }

    private static void await(BooleanSupplier condition) {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            assertTrue(System.nanoTime() < deadline, "timed out waiting for the taps to settle");
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }

    /** CoreAudio with a settable default output; every process plays audio, process object = pid + 1000. */
    private static final class FakeCoreAudio extends CoreAudioWrapper {
        volatile int defaultOutput = SPEAKERS;

        @Override
        public int getDefaultDevice(boolean output) {
            return defaultOutput;
        }

        @Override
        public @Nullable String deviceUid(int deviceId) {
            return uid(deviceId);
        }

        @Override
        public int translatePidToProcessObject(int pid) {
            return pid + 1000;
        }
    }

    private static final class FakeTap implements Tap {
        final int pid;
        final String uid;
        volatile float gain;
        volatile boolean muted;
        volatile boolean closed;

        FakeTap(int pid, String uid, float gain) {
            this.pid = pid;
            this.uid = uid;
            this.gain = gain;
        }

        @Override
        public void setGain(float gain) {
            this.gain = gain;
        }

        @Override
        public void setMuted(boolean muted) {
            this.muted = muted;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    /** Records every tap; can refuse taps on some outputs, and hold a creation until released. */
    private static final class FakeTaps implements Tap.Factory {
        final List<FakeTap> created = new CopyOnWriteArrayList<>();
        final Set<String> failingUids = ConcurrentHashMap.newKeySet();
        final CountDownLatch entered = new CountDownLatch(1);
        final AtomicInteger failures = new AtomicInteger();
        volatile @Nullable Thread blockedThread;
        private volatile CountDownLatch gate = new CountDownLatch(0);

        void block() {
            gate = new CountDownLatch(1);
        }

        void release() {
            gate.countDown();
        }

        @Override
        public Tap create(int processObject, String outputDeviceUid, float initialGain) {
            if (gate.getCount() > 0) {
                blockedThread = Thread.currentThread();
                entered.countDown();
                try {
                    gate.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (failingUids.contains(outputDeviceUid)) {
                failures.incrementAndGet();
                throw new ProcessTap.TapCreationException("AudioHardwareCreateAggregateDevice", 0x21646576);
            }
            var tap = new FakeTap(processObject - 1000, outputDeviceUid, initialGain);
            created.add(tap);
            return tap;
        }

        FakeTap live(int pid) {
            var live = created.stream().filter(t -> t.pid == pid && !t.closed).toList();
            assertEquals(1, live.size(), "one live tap for pid " + pid);
            return live.getFirst();
        }

        boolean liveOn(int pid, int deviceId) {
            return created.stream().anyMatch(t -> t.pid == pid && !t.closed && t.uid.equals(uid(deviceId)));
        }

        long liveCount() {
            return created.stream().filter(t -> !t.closed).count();
        }
    }
}
