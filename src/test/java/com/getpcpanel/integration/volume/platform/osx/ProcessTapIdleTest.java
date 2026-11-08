package com.getpcpanel.integration.volume.platform.osx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.jna.Pointer;

class ProcessTapIdleTest {
    private static final int PROCESS_OBJECT = 166;

    private FakeHal hal;
    private ProcessTap tap;

    @BeforeEach
    void setUp() {
        hal = new FakeHal();
        tap = new ProcessTap(1, 2, PROCESS_OBJECT, hal, 0.5f, false);
    }

    @Test
    void aTapStillWaitingForItsFirstSoundIsLeftAlone() {
        hal.runningOutput = 0;

        tap.checkIdle();

        assertEquals(List.of(), hal.calls);
    }

    @Test
    void aTapWhoseProcessStillPlaysKeepsRendering() {
        tap.render(null, null);
        hal.runningOutput = 1;

        tap.checkIdle();

        assertEquals(List.of(), hal.calls);
    }

    @Test
    void aTapWhoseProcessWentQuietWaitsForTheNextSoundOnce() {
        tap.render(null, null);
        hal.runningOutput = 0;

        tap.checkIdle();
        tap.checkIdle();

        assertEquals(List.of("stop", "start"), hal.calls);
        assertTrue(tap.alive());
    }

    @Test
    void aTapRenderingAgainIsPutBackToWaitingWhenItsProcessGoesQuietAgain() {
        tap.render(null, null);
        hal.runningOutput = 0;
        tap.checkIdle();

        tap.render(null, null);
        tap.checkIdle();

        assertEquals(List.of("stop", "start", "stop", "start"), hal.calls);
    }

    @Test
    void aTapOfAProcessThatCannotBeReadIsLeftForTheServiceToClose() {
        tap.render(null, null);
        hal.runningOutput = -1;

        tap.checkIdle();

        assertEquals(List.of(), hal.calls);
    }

    @Test
    void aTapThatCannotStartAgainReportsItselfDead() {
        tap.render(null, null);
        hal.runningOutput = 0;
        hal.startStatus = 0x73746f70;

        tap.checkIdle();
        tap.render(null, null);
        tap.checkIdle();

        assertFalse(tap.alive());
        assertEquals(List.of("stop", "start"), hal.calls);
    }

    private static final class FakeHal implements ProcessTap.Hal {
        final List<String> calls = new ArrayList<>();
        int runningOutput = 1;
        int startStatus;

        @Override
        public int runningOutput(int processObject) {
            assertEquals(PROCESS_OBJECT, processObject);
            return runningOutput;
        }

        @Override
        public int start(int aggregateId, Pointer ioProcId) {
            calls.add("start");
            return startStatus;
        }

        @Override
        public int stop(int aggregateId, Pointer ioProcId) {
            calls.add("stop");
            return 0;
        }
    }
}
