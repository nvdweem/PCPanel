package com.getpcpanel.device.provider.deej;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.util.TypeLiteral;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getpcpanel.device.descriptor.AnalogKind;
import com.getpcpanel.device.io.SerialTransport;
import com.getpcpanel.hid.DeviceCommunicationHandler.KnobRotateEvent;
import com.getpcpanel.hid.DeviceScanner.DeviceConnectedEvent;
import com.getpcpanel.hid.DeviceScanner.DeviceDisconnectedEvent;
import com.getpcpanel.profile.Save;
import com.getpcpanel.profile.SaveService;

@DisplayName("Deej serial provider (fake transport, no hardware)")
class DeejSerialProviderTest {
    private DeejSerialProvider provider;
    private FakeSerialTransport transport;
    private RecordingEvent events;
    private Save save;

    @BeforeEach
    void setUp() {
        provider = new DeejSerialProvider();
        transport = new FakeSerialTransport();
        events = new RecordingEvent();
        save = new Save();
        provider.transport = transport;
        provider.eventBus = events;
        provider.saveService = new SaveService() {
            @Override public Save get() { return save; }
            @Override public void save() { /* no file I/O in tests */ }
        };
    }

    @Test
    void firstValidLineLearnsSliderCountAndFiresConnectWithDescriptor() {
        provider.addManual("COM3", 9600, "default");
        transport.feed("0|512|1023\r\n");

        var connect = events.first(DeviceConnectedEvent.class);
        assertNotNull(connect, "first valid line must fire a connect event");
        var d = connect.descriptor();
        assertEquals("deej", d.providerId());
        assertEquals("deej", d.deviceKindId());
        assertEquals(3, d.analogInputs().size(), "slider count learned from the first line");
        assertTrue(d.analogInputs().stream().allMatch(a -> a.kind() == AnalogKind.SLIDER));
        assertTrue(d.analogInputs().stream().allMatch(a -> !a.hasButton()));
        assertEquals(0, d.digitalInputs().size());
        assertEquals(0, d.lightOutputs().size());
        assertEquals(null, d.globalLighting());
        assertEquals(0, d.analogInputs().get(0).sourceMin());
        assertEquals(1023, d.analogInputs().get(0).sourceMax());
    }

    @Test
    void firstLineEmitsInitialNormalizedValuesPerSlider() {
        var id = provider.addManual("COM3", 9600, "default");
        transport.feed("0|512|1023\r\n");

        var knobs = events.all(KnobRotateEvent.class);
        assertEquals(3, knobs.size());
        assertEquals(new KnobRotateEvent(id, 0, 0, true), knobs.get(0));
        assertEquals(new KnobRotateEvent(id, 1, 128, true), knobs.get(1)); // 512 -> 128
        assertEquals(new KnobRotateEvent(id, 2, 255, true), knobs.get(2)); // 1023 -> 255
    }

    @Test
    void subsequentSignificantChangeEmitsKnobRotate() {
        var id = provider.addManual("COM3", 9600, "default");
        transport.feed("500|500\r\n"); // learn 2 sliders
        events.clear();

        // slider 0 moves by 50/1023 ≈ 0.049 (> 0.025) -> emit; slider 1 unchanged
        transport.feed("550|500\r\n");

        var knobs = events.all(KnobRotateEvent.class);
        assertEquals(1, knobs.size());
        assertEquals(0, knobs.get(0).knob());
        assertEquals(DeejProtocol.normalize(550), knobs.get(0).value());
        assertFalse(knobs.get(0).initial());
    }

    @Test
    void subThresholdJitterIsSuppressed() {
        provider.addManual("COM3", 9600, "default");
        transport.feed("500|500\r\n");
        events.clear();

        transport.feed("503|501\r\n"); // both deltas < 0.025 -> suppressed
        assertEquals(0, events.all(KnobRotateEvent.class).size());
    }

    @Test
    void railSnapAlwaysEmittedEvenIfSmallDelta() {
        provider.addManual("COM3", 9600, "default");
        transport.feed("1020\r\n"); // learn 1 slider, near top
        events.clear();

        transport.feed("1023\r\n"); // delta 3/1023 < 0.025 but snaps to the top rail -> emit
        var knobs = events.all(KnobRotateEvent.class);
        assertEquals(1, knobs.size());
        assertEquals(255, knobs.get(0).value());
    }

    @Test
    void garbageLinesAreSkippedAndDoNotConnect() {
        provider.addManual("COM3", 9600, "default");
        transport.feed("garbage\r\n");
        transport.feed("12|ab\r\n");
        transport.feed("\r\n");

        assertEquals(0, events.all(DeviceConnectedEvent.class).size());
        assertEquals(0, events.all(KnobRotateEvent.class).size());
    }

    @Test
    void garbageAfterConnectIsSkippedButGoodLinesStillProcess() {
        var id = provider.addManual("COM3", 9600, "default");
        transport.feed("100|100\r\n"); // learn
        events.clear();

        transport.feed("nonsense\r\n"); // skipped
        transport.feed("200|100\r\n"); // slider 0 changed

        var knobs = events.all(KnobRotateEvent.class);
        assertEquals(1, knobs.size());
        assertEquals(id, knobs.get(0).serialNum());
        assertEquals(0, knobs.get(0).knob());
    }

    @Test
    void removeManualFiresDisconnect() {
        var id = provider.addManual("COM3", 9600, "default");
        transport.feed("100|100\r\n");
        events.clear();

        provider.removeManual(id);

        assertNotNull(events.first(DeviceDisconnectedEvent.class));
        assertEquals(id, events.first(DeviceDisconnectedEvent.class).serialNum());
        assertTrue(transport.lastClosed, "the serial connection must be closed on remove");
    }

    @Test
    void transportErrorFiresDisconnect() {
        var id = provider.addManual("COM3", 9600, "default");
        transport.feed("100|100\r\n");
        events.clear();

        transport.fail(new RuntimeException("cable yanked"));

        assertNotNull(events.first(DeviceDisconnectedEvent.class));
        assertEquals(id, events.first(DeviceDisconnectedEvent.class).serialNum());
    }

    @Test
    void addManualPersistsPortAndBaud() {
        var id = provider.addManual("COM7", 19200, "high");

        var ds = save.getDeviceSave(id);
        assertNotNull(ds, "a DeviceSave must be created for the manual device");
        assertEquals("deej", ds.getProviderId());
        assertEquals("deej", ds.getDeviceKindId());
        assertNotNull(ds.getProviderConfig());
        assertEquals("COM7", ds.getProviderConfig().get(DeejSerialProvider.CFG_PORT));
        assertEquals("19200", ds.getProviderConfig().get(DeejSerialProvider.CFG_BAUD));
        assertEquals("high", ds.getProviderConfig().get(DeejSerialProvider.CFG_NOISE));
    }

    @Test
    void startReconnectsPersistedDevices() {
        // Persist a Deej device directly, then start() should reconnect it.
        var seed = provider.addManual("COM9", 9600, "default");
        // Drop the in-memory connection (simulate a fresh process) but keep the persisted save.
        provider.stop();
        transport.reset();

        provider.start();
        transport.feed("0|0|0|0\r\n");

        var connect = events.first(DeviceConnectedEvent.class);
        assertNotNull(connect, "persisted device should reconnect and connect on first line");
        assertEquals(4, connect.descriptor().analogInputs().size());
        assertEquals(seed, connect.serialNum());
    }

    // ── Fakes ────────────────────────────────────────────────────────────────

    /** An in-memory transport: captures the line/error consumers so the test can drive them. */
    private static final class FakeSerialTransport implements SerialTransport {
        private Consumer<String> onLine;
        private Consumer<Throwable> onError;
        private FakeConnection connection;
        boolean lastClosed;

        @Override
        public List<PortInfo> listPorts() {
            return List.of(new PortInfo("COM3", "Fake Port"));
        }

        @Override
        public SerialConnection open(String portName, int baud, Consumer<String> onLine, Consumer<Throwable> onError) {
            this.onLine = onLine;
            this.onError = onError;
            connection = new FakeConnection(portName);
            lastClosed = false;
            return connection;
        }

        void feed(String line) {
            if (onLine != null) {
                onLine.accept(line);
            }
        }

        void fail(Throwable t) {
            if (onError != null) {
                onError.accept(t);
            }
        }

        void reset() {
            onLine = null;
            onError = null;
            connection = null;
        }

        private final class FakeConnection implements SerialConnection {
            private final String portName;
            private boolean open = true;

            private FakeConnection(String portName) {
                this.portName = portName;
            }

            @Override public String portName() { return portName; }
            @Override public boolean isOpen() { return open; }
            @Override public void close() { open = false; lastClosed = true; }
        }
    }

    /** A fake CDI Event that records everything fired, so tests can assert on emitted events. */
    private static final class RecordingEvent implements Event<Object> {
        private final List<Object> fired = new ArrayList<>();

        @Override public void fire(Object event) { fired.add(event); }
        @Override public <U> CompletionStage<U> fireAsync(U event) { fired.add(event); return null; }
        @Override public <U> CompletionStage<U> fireAsync(U event, jakarta.enterprise.event.NotificationOptions options) { fired.add(event); return null; }
        @Override public Event<Object> select(Annotation... qualifiers) { return this; }
        @Override public <U> Event<U> select(Class<U> subtype, Annotation... qualifiers) { throw new UnsupportedOperationException(); }
        @Override public <U> Event<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) { throw new UnsupportedOperationException(); }

        void clear() { fired.clear(); }

        @SuppressWarnings("unchecked")
        <T> List<T> all(Class<T> type) {
            var out = new ArrayList<T>();
            for (var e : fired) {
                if (type.isInstance(e)) {
                    out.add((T) e);
                }
            }
            return out;
        }

        <T> T first(Class<T> type) {
            var all = all(type);
            return all.isEmpty() ? null : all.get(0);
        }
    }
}
