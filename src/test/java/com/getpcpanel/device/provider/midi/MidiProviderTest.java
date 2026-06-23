package com.getpcpanel.device.provider.midi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.getpcpanel.device.io.MidiTransport;
import com.getpcpanel.hid.DeviceCommunicationHandler.ButtonPressEvent;
import com.getpcpanel.hid.DeviceCommunicationHandler.KnobRotateEvent;
import com.getpcpanel.hid.DeviceScanner.DeviceConnectedEvent;
import com.getpcpanel.hid.DeviceScanner.DeviceDisconnectedEvent;
import com.getpcpanel.profile.Save;
import com.getpcpanel.profile.SaveService;

@DisplayName("MIDI provider (fake transport, no hardware)")
class MidiProviderTest {
    private MidiProvider provider;
    private FakeMidiTransport transport;
    private RecordingEvent events;
    private Save save;

    @BeforeEach
    void setUp() {
        provider = new MidiProvider();
        transport = new FakeMidiTransport();
        events = new RecordingEvent();
        save = new Save();
        provider.transport = transport;
        provider.eventBus = events;
        provider.saveService = new SaveService() {
            @Override public Save get() { return save; }
            @Override public void save() { /* no file I/O in tests */ }
        };
    }

    private String startAndOpenOne() {
        transport.addInput("nanoKONTROL", "Korg");
        provider.start();
        return MidiProvider.deviceIdFor("nanoKONTROL");
    }

    @Test
    void autoDiscoveryOpensInputAndFiresConnectWithEmptyDescriptor() {
        var id = startAndOpenOne();
        var connect = events.first(DeviceConnectedEvent.class);
        assertNotNull(connect, "an auto-discovered MIDI input must fire a connect event");
        assertEquals(id, connect.serialNum());
        var d = connect.descriptor();
        assertEquals("midi", d.providerId());
        assertEquals("midi", d.deviceKindId());
        assertEquals("nanoKONTROL", d.displayName());
        // Starts with no controls; grows as messages arrive.
        assertEquals(0, d.analogInputs().size());
        assertEquals(0, d.digitalInputs().size());
        assertNull(d.globalLighting(), "MIDI device is lightless (no output until Phase 6)");
    }

    @Test
    void controlChangeGrowsDescriptorAndEmitsNormalizedKnobRotate() {
        var id = startAndOpenOne();
        events.clear();

        transport.feed(0xB0, 7, 127); // CC7 ch0 full

        // Descriptor grew: a new connect event carries the learned analog input.
        var connect = events.last(DeviceConnectedEvent.class);
        assertNotNull(connect);
        assertEquals(1, connect.descriptor().analogInputs().size());
        var spec = connect.descriptor().analogInputs().get(0);
        assertEquals("cc7.ch0", spec.id());
        assertEquals(AnalogKind.KNOB, spec.kind());
        assertEquals(0, spec.sourceMin());
        assertEquals(127, spec.sourceMax());

        // And a normalized KnobRotateEvent at the assigned index.
        var knobs = events.all(KnobRotateEvent.class);
        assertEquals(1, knobs.size());
        assertEquals(id, knobs.get(0).serialNum());
        assertEquals(0, knobs.get(0).knob());
        assertEquals(255, knobs.get(0).value()); // 127 -> 255
    }

    @Test
    void noteOnAndNoteOffEmitButtonPressAndRelease() {
        startAndOpenOne();
        events.clear();

        transport.feed(0x90, 36, 100); // NOTE_ON ch0 note36 -> press
        transport.feed(0x80, 36, 0);   // NOTE_OFF -> release

        var buttons = events.all(ButtonPressEvent.class);
        assertEquals(2, buttons.size());
        assertEquals(0, buttons.get(0).button());
        assertTrue(buttons.get(0).pressed());
        assertFalse(buttons.get(1).pressed());

        // The note became a DigitalInputSpec in the grown descriptor.
        var connect = events.last(DeviceConnectedEvent.class);
        assertEquals(1, connect.descriptor().digitalInputs().size());
        assertEquals("note36.ch0", connect.descriptor().digitalInputs().get(0).id());
    }

    @Test
    void noteOnVelocityZeroIsRelease() {
        startAndOpenOne();
        events.clear();
        transport.feed(0x90, 36, 0); // NOTE_ON velocity 0 == release
        var buttons = events.all(ButtonPressEvent.class);
        assertEquals(1, buttons.size());
        assertFalse(buttons.get(0).pressed());
    }

    @Test
    void ccAndNoteDoNotShareIndices() {
        startAndOpenOne();
        events.clear();
        transport.feed(0xB0, 7, 64);   // CC -> analog index 0
        transport.feed(0x90, 36, 100); // NOTE -> digital index 0 (separate space)

        var connect = events.last(DeviceConnectedEvent.class);
        var analog = connect.descriptor().analogInputs();
        var digital = connect.descriptor().digitalInputs();
        assertEquals(1, analog.size());
        assertEquals(1, digital.size());
        assertEquals(0, analog.get(0).index());
        assertEquals(0, digital.get(0).index());
        // Distinct ids -> the rest of the app keys analog idx 0 and digital idx 0 to different controls.
        assertEquals("cc7.ch0", analog.get(0).id());
        assertEquals("note36.ch0", digital.get(0).id());
    }

    @Test
    void indexStableAcrossRepeatedMessages() {
        startAndOpenOne();
        events.clear();
        transport.feed(0xB0, 7, 10);
        transport.feed(0xB0, 8, 10); // a second CC -> index 1
        transport.feed(0xB0, 7, 20); // CC7 again -> still index 0

        var knobs = events.all(KnobRotateEvent.class);
        assertEquals(3, knobs.size());
        assertEquals(0, knobs.get(0).knob());
        assertEquals(1, knobs.get(1).knob());
        assertEquals(0, knobs.get(2).knob()); // stable
    }

    @Test
    void descriptorOnlyGrowsOncePerControl() {
        startAndOpenOne();
        events.clear();
        transport.feed(0xB0, 7, 10);
        transport.feed(0xB0, 7, 20);
        transport.feed(0xB0, 7, 30);

        // Only the first CC7 grew the descriptor (one extra connect event); the rest are value updates.
        assertEquals(1, events.all(DeviceConnectedEvent.class).size());
        assertEquals(3, events.all(KnobRotateEvent.class).size());
    }

    @Test
    void unknownMessagesAreIgnored() {
        startAndOpenOne();
        events.clear();
        transport.feed(0xE0, 0, 64); // pitch-bend
        transport.feed(0xC0, 5, 0);  // program-change
        transport.feed(0xF8, 0, 0);  // clock

        assertEquals(0, events.all(KnobRotateEvent.class).size());
        assertEquals(0, events.all(ButtonPressEvent.class).size());
        assertEquals(0, events.all(DeviceConnectedEvent.class).size());
    }

    @Test
    void emptyDeviceListDegradesGracefully() {
        // No inputs at all (a broken/absent MIDI backend): start must not throw and fire nothing.
        provider.start();
        assertEquals(0, events.all(DeviceConnectedEvent.class).size());
        assertTrue(provider.detected().isEmpty());
    }

    @Test
    void listInputsThrowingDoesNotCrashStart() {
        transport.failListing = true;
        provider.start(); // must swallow the Throwable
        assertEquals(0, events.all(DeviceConnectedEvent.class).size());
    }

    @Test
    void transportErrorFiresDisconnect() {
        var id = startAndOpenOne();
        events.clear();
        transport.fail(new RuntimeException("cable yanked"));

        var disc = events.first(DeviceDisconnectedEvent.class);
        assertNotNull(disc);
        assertEquals(id, disc.serialNum());
    }

    @Test
    void messagesPersistLearnedIndexMap() {
        var id = startAndOpenOne();
        transport.feed(0xB0, 7, 64);
        transport.feed(0x90, 36, 100);

        var ds = save.getDeviceSave(id);
        assertNotNull(ds);
        assertEquals("midi", ds.getProviderId());
        var cfg = ds.getProviderConfig();
        assertNotNull(cfg);
        assertEquals("nanoKONTROL", cfg.get(MidiProvider.CFG_NAME));
        assertEquals("0", cfg.get("cc7.ch0"));
        assertEquals("0", cfg.get("note36.ch0"));
    }

    @Test
    void reconnectRehydratesPersistedIndicesStably() {
        var id = startAndOpenOne();
        transport.feed(0xB0, 7, 64);
        transport.feed(0xB0, 8, 64); // cc8 -> index 1
        provider.stop();
        transport.resetConnection();
        events.clear();

        // Fresh process: the persisted save survives; reconnecting must keep cc8 at index 1.
        provider.start();
        var connect = events.first(DeviceConnectedEvent.class);
        assertNotNull(connect, "persisted MIDI device reconnects on rescan");
        assertEquals(id, connect.serialNum());
        // The descriptor is rehydrated with both learned controls.
        assertEquals(2, connect.descriptor().analogInputs().size());

        transport.feed(0xB0, 8, 100); // cc8 again -> still index 1
        var knobs = events.all(KnobRotateEvent.class);
        assertEquals(1, knobs.get(knobs.size() - 1).knob());
    }

    @Test
    void detectedReportsConnectedState() {
        startAndOpenOne();
        var detected = provider.detected();
        assertEquals(1, detected.size());
        assertEquals("nanoKONTROL", detected.get(0).name());
        assertTrue(detected.get(0).connected());
    }

    @Test
    void unpluggedDeviceIsDisconnectedOnRescan() {
        startAndOpenOne();
        events.clear();

        transport.removeInput("nanoKONTROL"); // user unplugs the controller
        provider.rescan();

        assertNotNull(events.first(DeviceDisconnectedEvent.class), "an unplugged MIDI device must disconnect on rescan");
        assertTrue(provider.detected().isEmpty(), "and no longer be reported as connected");
    }

    @Test
    void rescanReopensAReconnectedDevice() {
        startAndOpenOne();
        transport.removeInput("nanoKONTROL");
        provider.rescan(); // disconnect the unplugged device
        events.clear();

        transport.addInput("nanoKONTROL", "Korg"); // plug it back in
        provider.rescan();

        assertNotNull(events.first(DeviceConnectedEvent.class), "re-plugging a previously-unplugged device reopens it");
    }

    @Test
    void transientListingFailureDoesNotDisconnectHealthyDevices() {
        startAndOpenOne();
        events.clear();

        transport.failListing = true; // a transient backend hiccup: enumeration throws
        provider.rescan();

        assertNull(events.first(DeviceDisconnectedEvent.class), "a failed enumeration must not be treated as 'all unplugged'");
        transport.failListing = false;
        assertFalse(provider.detected().isEmpty(), "the device is still there once enumeration recovers");
    }

    // ── Fakes ────────────────────────────────────────────────────────────────

    /** An in-memory MIDI transport: captures the consumers so the test can feed canned messages. */
    private static final class FakeMidiTransport implements MidiTransport {
        private final List<MidiDeviceInfo> inputs = new ArrayList<>();
        private Consumer<MidiMessage> onMessage;
        private Consumer<Throwable> onError;
        boolean failListing;

        void addInput(String id, String vendor) {
            inputs.add(new MidiDeviceInfo(id, id));
        }

        void removeInput(String id) {
            inputs.removeIf(i -> i.id().equals(id));
        }

        @Override
        public List<MidiDeviceInfo> listInputs() {
            if (failListing) {
                throw new RuntimeException("MIDI backend unavailable");
            }
            return new ArrayList<>(inputs);
        }

        @Override
        public MidiConnection open(String id, Consumer<MidiMessage> onMessage, Consumer<Throwable> onError) {
            this.onMessage = onMessage;
            this.onError = onError;
            return new FakeConnection(id);
        }

        void feed(int status, int data1, int data2) {
            if (onMessage != null) {
                onMessage.accept(new MidiMessage(status, data1, data2));
            }
        }

        void fail(Throwable t) {
            if (onError != null) {
                onError.accept(t);
            }
        }

        void resetConnection() {
            onMessage = null;
            onError = null;
        }

        private static final class FakeConnection implements MidiConnection {
            private final String id;
            private boolean open = true;

            private FakeConnection(String id) {
                this.id = id;
            }

            @Override public String deviceId() { return id; }
            @Override public boolean isOpen() { return open; }
            @Override public void close() { open = false; }
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

        <T> T last(Class<T> type) {
            var all = all(type);
            return all.isEmpty() ? null : all.get(all.size() - 1);
        }
    }
}
