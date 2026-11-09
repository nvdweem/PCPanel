package com.getpcpanel.device.provider.pcpanel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import org.hid4java.HidDevice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.getpcpanel.device.provider.pcpanel.DeviceCommunicationHandler.KnobRotateEvent;
import com.getpcpanel.profile.Save;
import com.getpcpanel.profile.SaveService;

import jakarta.enterprise.event.Event;

/** The slider rolling average sends its settled value and then lets its thread wait for the next movement. */
class RollingAverageTest {
    private static final String SERIAL = "rolling-test";
    private static final int SLIDER = 5;
    private static final long TIMEOUT_MS = 5_000;

    private final List<KnobRotateEvent> sent = new CopyOnWriteArrayList<>();
    private DeviceCommunicationHandler handler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        sent.clear();
        var save = new Save();
        save.setSliderRollingAverage(50);
        var saveService = mock(SaveService.class);
        when(saveService.get()).thenReturn(save);
        Event<Object> events = mock(Event.class);
        doAnswer(inv -> {
            if (inv.getArgument(0) instanceof KnobRotateEvent e) {
                sent.add(e);
            }
            return null;
        }).when(events).fire(any());
        var device = mock(HidDevice.class);
        when(device.getSerialNumber()).thenReturn(SERIAL);
        // Not connected as far as the scanner knows, so the reader and writer threads end at once.
        handler = new DeviceCommunicationHandler(mock(DeviceScanner.class), saveService, events, SERIAL, device,
                DescriptorFactory.forType(DeviceType.PCPANEL_PRO));
        handler.start();
    }

    @AfterEach
    void tearDown() {
        handler.stopGracefully(1_000);
    }

    @Test
    void sendsTheSettledValueAndWaitsUntilTheSliderMovesAgain() throws InterruptedException {
        slide(true, 10);
        slide(false, 100);
        slide(false, 120);

        assertTrue(eventually(() -> lastSent() == 120), "the average settles on the last value");
        assertTrue(eventually(this::rollingThreadWaits), "the thread waits once the slider has settled");
        var settled = sent.size();
        Thread.sleep(200);
        assertEquals(settled, sent.size(), "nothing more is sent while the slider rests");

        slide(false, 200);
        assertTrue(eventually(() -> lastSent() == 200), "a new movement is averaged again");
        assertTrue(eventually(this::rollingThreadWaits), "and the thread waits again after it settles");
    }

    private void slide(boolean initial, int value) {
        handler.interpretInputData(initial, new byte[] { 1, SLIDER, (byte) value });
    }

    private int lastSent() {
        return sent.isEmpty() ? -1 : sent.getLast().value();
    }

    private boolean rollingThreadWaits() {
        return Thread.getAllStackTraces().keySet().stream()
                     .filter(t -> t.getName().equals("HIDRolling " + SERIAL))
                     .anyMatch(t -> t.getState() == Thread.State.WAITING);
    }

    private static boolean eventually(BooleanSupplier condition) throws InterruptedException {
        var deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(20);
        }
        return condition.getAsBoolean();
    }
}
