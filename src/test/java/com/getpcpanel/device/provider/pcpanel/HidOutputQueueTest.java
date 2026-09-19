package com.getpcpanel.device.provider.pcpanel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class HidOutputQueueTest {
    private static final byte[] INIT = { 1 };

    private static byte[] frame(int value) {
        return new byte[] { 6, 2, (byte) value };
    }

    /** A brightness sweep produces a full lighting state per tick; only the newest unwritten one is worth writing. */
    @Test
    void newerLightingReplacesLightingNotYetWritten() throws InterruptedException {
        var sut = new HidOutputQueue();
        sut.replaceLighting(frame(1));
        sut.replaceLighting(frame(2));
        sut.replaceLighting(frame(3));

        assertArrayEquals(new byte[][] { frame(3) }, sut.next(0));
        sut.written();
        assertNull(sut.next(0), "the replaced lighting states must not be written afterwards");
    }

    /** A multi-report state (the Pro's knobs, labels, sliders and logo) is replaced as a whole. */
    @Test
    void lightingIsReplacedAsAWholeSet() throws InterruptedException {
        var sut = new HidOutputQueue();
        sut.replaceLighting(frame(1), frame(2), frame(3), frame(4));
        sut.replaceLighting(frame(5), frame(6), frame(7), frame(8));

        assertArrayEquals(new byte[][] { frame(5), frame(6), frame(7), frame(8) }, sut.next(0));
    }

    @Test
    void messagesAreWrittenInOrderAndNeverDropped() throws InterruptedException {
        var sut = new HidOutputQueue();
        sut.enqueue(frame(1), frame(2));
        sut.enqueue(frame(3));

        assertArrayEquals(new byte[][] { frame(1) }, sut.next(0));
        assertArrayEquals(new byte[][] { frame(2) }, sut.next(0));
        assertArrayEquals(new byte[][] { frame(3) }, sut.next(0));
    }

    /** The device init must still reach the panel before the lighting that follows it. */
    @Test
    void lightingKeepsItsPlaceBehindEarlierMessages() throws InterruptedException {
        var sut = new HidOutputQueue();
        sut.enqueue(INIT);
        sut.replaceLighting(frame(1));
        sut.replaceLighting(frame(2));

        assertArrayEquals(new byte[][] { INIT }, sut.next(0));
        assertArrayEquals(new byte[][] { frame(2) }, sut.next(0));
    }

    /** Lighting that arrives while the previous state is being written is written next - not lost, not duplicated. */
    @Test
    void lightingArrivingDuringAWriteIsWrittenNext() throws InterruptedException {
        var sut = new HidOutputQueue();
        sut.replaceLighting(frame(1));
        assertArrayEquals(new byte[][] { frame(1) }, sut.next(0));

        sut.replaceLighting(frame(2));
        sut.written();

        assertArrayEquals(new byte[][] { frame(2) }, sut.next(0));
        sut.written();
        assertNull(sut.next(0));
    }

    /** Shutdown waits on this so the "lights off" state reaches the panel before the app exits. */
    @Test
    void idleOnlyOnceEverythingIsWritten() throws InterruptedException {
        var sut = new HidOutputQueue();
        assertTrue(sut.isIdle());

        sut.enqueue(INIT);
        sut.replaceLighting(frame(1));
        sut.replaceLighting(frame(2));
        assertFalse(sut.isIdle());

        sut.next(0);
        sut.written();
        assertFalse(sut.isIdle(), "lighting is still pending");

        sut.next(0);
        assertFalse(sut.isIdle(), "lighting is taken but not yet written");
        sut.written();
        assertTrue(sut.isIdle());
    }

    @Test
    void clearDropsEverythingPending() throws InterruptedException {
        var sut = new HidOutputQueue();
        sut.enqueue(INIT);
        sut.replaceLighting(frame(1));

        sut.clear();

        assertTrue(sut.isIdle());
        assertNull(sut.next(0));
    }

    /** Under a real producer/writer race the panel always ends on the newest state and nothing is left over. */
    @Test
    void writerAlwaysEndsOnTheNewestLighting() {
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            var sut = new HidOutputQueue();
            var lastWritten = new AtomicInteger(-1);
            var writer = new Thread(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        var frames = sut.next(10);
                        if (frames != null) {
                            lastWritten.set(frames[0][2] & 0xFF);
                            sut.written();
                        }
                    }
                } catch (InterruptedException ignored) {
                    // Test finished.
                }
            });
            writer.start();

            for (var i = 0; i < 20_000; i++) {
                sut.replaceLighting(frame(i % 256));
            }
            var newest = (20_000 - 1) % 256;
            while (!sut.isIdle()) {
                Thread.onSpinWait();
            }
            writer.interrupt();
            writer.join();

            assertEquals(newest, lastWritten.get());
        });
    }
}
