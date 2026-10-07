package dev.niels.pulse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.niels.pulse.PulseClient.Command;
import dev.niels.pulse.PulseClient.DeviceRef;
import dev.niels.pulse.model.SubscriptionEvent;
import dev.niels.pulse.model.SubscriptionEvent.Facility;

class PulseClientTest {
    private static final Duration TIMEOUT = Duration.ofMillis(500);

    @TempDir Path dir;
    private FakePulseServer server;
    private PulseClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new FakePulseServer(dir);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (client != null) {
            client.close();
        }
        server.close();
    }

    private PulseClient connect() throws IOException {
        client = PulseClient.connect(server.socket(), new byte[0], Map.of("application.name", "test"), TIMEOUT);
        return client;
    }

    /** A sink as a version-32 server writes it ({@code sink_fill_tagstruct}). */
    static void writeSink(TagWriter w, int index, String name, long volume, boolean mute, Map<String, String> props) {
        w.putU32(index).putString(name).putString(name + " description");
        sampleSpec(w);
        channelMap(w);
        w.putU32(0xFFFFFFFFL); // owner module
        w.putCVolume(new ChannelVolumes(volume, volume));
        w.putBoolean(mute);
        w.putU32(index + 100).putString(name + ".monitor");
        usec(w);
        w.putString("module-null-sink.c").putU32(0);
        w.putProplist(props);
        usec(w);
        volume(w);
        w.putU32(0).putU32(65537).putU32(0xFFFFFFFFL); // state, volume steps, card
        w.putU32(1).putString("analog-output").putString("Speakers").putU32(9000).putU32(2); // one port, v24 available
        w.putString("analog-output");
        w.putU8(1);
        formatInfo(w);
    }

    /** A sink input as a version-32 server writes it. */
    static void writeSinkInput(TagWriter w, int index, int sink, long volume, boolean mute, boolean corked, Map<String, String> props) {
        w.putU32(index).putString("playback").putU32(0xFFFFFFFFL).putU32(3).putU32(sink);
        sampleSpec(w);
        channelMap(w);
        w.putCVolume(new ChannelVolumes(volume, volume));
        usec(w);
        usec(w);
        w.putString("speex-float-1").putString("protocol-native.c");
        w.putBoolean(mute);
        w.putProplist(props);
        w.putBoolean(corked);
        w.putBoolean(true).putBoolean(true);
        formatInfo(w);
    }

    private static void sampleSpec(TagWriter w) {
        w.putRaw(new byte[] { 'a', 3, 2, 0, 0, (byte) 0xAC, 0x44 }); // float32le, 2 channels, 44100 Hz
    }

    private static void channelMap(TagWriter w) {
        w.putRaw(new byte[] { 'm', 2, 1, 2 });
    }

    private static void usec(TagWriter w) {
        w.putRaw(new byte[] { 'U', 0, 0, 0, 0, 0, 0, 0, 0 });
    }

    private static void volume(TagWriter w) {
        w.putRaw(new byte[] { 'V', 0, 1, 0, 0 });
    }

    private static void formatInfo(TagWriter w) {
        w.putRaw(new byte[] { 'f' });
        w.putU8(1).putProplist(Map.of());
    }

    @Test
    void negotiatesTheLowerVersion() throws IOException {
        server.serverVersion = 35 | 0x80000000;
        assertEquals(32, connect().version());
        assertEquals(35, client.serverVersion());
    }

    @Test
    void refusesAServerOlderThanProplists() {
        server.serverVersion = 12;
        assertThrows(PulseException.class, this::connect);
    }

    @Test
    void readsSinkList() throws IOException {
        server.on(Command.GET_SINK_INFO_LIST, (in, out) -> {
            writeSink(out, 0, "alsa_output", 65536, false, Map.of("device.class", "sound"));
            writeSink(out, 4, "null", 32768, true, Map.of());
        });

        var sinks = connect().sinks();

        assertEquals(2, sinks.size());
        var first = sinks.getFirst();
        assertEquals(0, first.index());
        assertEquals("alsa_output", first.name());
        assertEquals("alsa_output description", first.description());
        assertEquals(new ChannelVolumes(65536, 65536), first.volume());
        assertFalse(first.muted());
        assertEquals(100, first.monitor());
        assertEquals("sound", first.properties().get("device.class"));
        assertEquals("analog-output", first.activePort());
        assertEquals("null", sinks.get(1).name());
        assertTrue(sinks.get(1).muted());
        assertEquals(32768, sinks.get(1).volume().max());
    }

    @Test
    void readsSinkInputs() throws IOException {
        server.on(Command.GET_SINK_INPUT_INFO_LIST, (in, out) -> {
            writeSinkInput(out, 12, 0, 40000, true, false, Map.of("application.process.binary", "firefox"));
            writeSinkInput(out, 13, 4, 1000, false, true, Map.of());
        });

        var streams = connect().sinkInputs();

        assertEquals(List.of(12, 13), streams.stream().map(s -> s.index()).toList());
        assertEquals("firefox", streams.getFirst().properties().get("application.process.binary"));
        assertTrue(streams.getFirst().muted());
        assertFalse(streams.getFirst().corked());
        assertEquals(4, streams.get(1).device());
        assertTrue(streams.get(1).corked());
    }

    @Test
    void readsOneSinkInput() throws IOException {
        server.on(Command.GET_SINK_INPUT_INFO, (in, out) -> writeSinkInput(out, (int) in.getU32(), 0, 40000, false, false, Map.of()));

        var stream = connect().sinkInput(21);

        assertEquals(21, stream.index());
        assertEquals(new ChannelVolumes(40000, 40000), stream.volume());
    }

    @Test
    void writesVolumeAndMuteArguments() throws IOException {
        var seen = new LinkedBlockingQueue<String>();
        server.on(Command.SET_SINK_VOLUME, (in, out) -> seen.add(in.getU32() + " " + in.getString() + " " + in.getCVolume()))
              .on(Command.SET_SINK_INPUT_MUTE, (in, out) -> seen.add(in.getU32() + " " + in.getBoolean()))
              .on(Command.MOVE_SINK_INPUT, (in, out) -> seen.add(in.getU32() + " " + in.getIndex() + " " + in.getString()));
        connect();

        client.setSinkVolume(DeviceRef.DEFAULT_SINK, ChannelVolumes.uniform(2, 1234));
        client.setSinkInputMute(9, true);
        client.moveSinkInput(9, "null");

        assertEquals(List.of("4294967295 @DEFAULT_SINK@ [1234, 1234]", "9 true", "9 -1 null"), List.copyOf(seen));
    }

    @Test
    void serverErrorsCarryTheirCode() throws IOException {
        server.fail(Command.GET_SINK_INFO, PulseException.ERR_NOENTITY);
        connect();

        var e = assertThrows(PulseException.class, () -> client.sink(DeviceRef.byIndex(99)));
        assertEquals(PulseException.ERR_NOENTITY, e.getCode());
        assertTrue(e.isServerError());
        assertTrue(client.isOpen(), "an error reply leaves the connection usable");
    }

    @Test
    void anUnansweredRequestTimesOutAndTheConnectionStaysUsable() throws IOException {
        server.ignore(Command.GET_SERVER_INFO);
        connect();

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> assertThrows(PulseTimeoutException.class, client::serverInfo));
        client.setDefaultSink("null");
        assertTrue(server.received.contains(Command.SET_DEFAULT_SINK));
    }

    @Test
    void deliversSubscriptionEvents() throws Exception {
        var events = new LinkedBlockingQueue<SubscriptionEvent>();
        connect().subscribe(Set.of(Facility.SINK, Facility.SINK_INPUT), events::add);

        server.event(0x02, 12); // new sink-input
        server.event(0x10, 0); // change sink
        server.event(0x22, 12); // remove sink-input

        assertEquals(new SubscriptionEvent(Facility.SINK_INPUT, SubscriptionEvent.Type.NEW, 12), events.poll(2, TimeUnit.SECONDS));
        assertEquals(new SubscriptionEvent(Facility.SINK, SubscriptionEvent.Type.CHANGE, 0), events.poll(2, TimeUnit.SECONDS));
        assertEquals(new SubscriptionEvent(Facility.SINK_INPUT, SubscriptionEvent.Type.REMOVE, 12), events.poll(2, TimeUnit.SECONDS));
    }

    /** PulseAudio rejects a mask with bits it does not know, so {@link Facility#UNKNOWN} must add none. */
    @Test
    void subscribingToEverythingSendsOnlyKnownBits() throws Exception {
        var mask = new LinkedBlockingQueue<Long>();
        server.on(Command.SUBSCRIBE, (in, out) -> mask.add(in.getU32()));

        connect().subscribe(Set.of(Facility.values()), e -> {
        });

        assertEquals(0x2FFL, mask.poll(2, TimeUnit.SECONDS));
    }

    /** A listener reacting to an event by asking the server for the new state must not deadlock the reader. */
    @Test
    void aListenerMayMakeRequests() throws Exception {
        server.on(Command.GET_SINK_INFO_LIST, (in, out) -> writeSink(out, 0, "s", 65536, false, Map.of()));
        var names = new LinkedBlockingQueue<String>();
        connect().subscribe(Set.of(Facility.SINK), e -> names.add(client.sinks().getFirst().name()));

        server.event(0x10, 0);

        assertEquals("s", names.poll(2, TimeUnit.SECONDS));
    }

    @Test
    void aDroppedConnectionFailsPendingRequestsAndCompletesClosed() throws Exception {
        server.ignore(Command.GET_SERVER_INFO);
        connect();
        var call = new Thread(() -> assertThrows(PulseException.class, client::serverInfo));
        call.start();
        Thread.sleep(100);

        server.disconnect();

        call.join(2000);
        assertFalse(call.isAlive());
        client.closed().get(2, TimeUnit.SECONDS);
        assertFalse(client.isOpen());
        var e = assertThrows(PulseException.class, () -> client.setDefaultSink("x"));
        assertFalse(e.isServerError());
    }

    @Test
    void readsServerInfo() throws IOException {
        server.on(Command.GET_SERVER_INFO, (in, out) -> {
            out.putString("PulseAudio (on PipeWire 1.0.5)").putString("15.0.0").putString("niels").putString("host");
            sampleSpec(out);
            out.putString("alsa_output").putString("alsa_input").putU32(1);
            channelMap(out);
        });

        var info = connect().serverInfo();

        assertEquals("alsa_output", info.defaultSinkName());
        assertEquals("alsa_input", info.defaultSourceName());
        assertEquals("PulseAudio (on PipeWire 1.0.5)", info.serverName());
        assertEquals("15.0.0", info.serverVersion());
        assertEquals("niels", info.userName());
        assertEquals("host", info.hostName());
    }

    /** Reads a version-32 {@code CREATE_RECORD_STREAM} the way the server does ({@code command_create_record_stream}). */
    private static String readRecordRequest(TagReader in) {
        var spec = in.getSampleSpec();
        var channels = in.getChannelMap();
        in.getU32(); // source index
        var source = in.getString();
        in.getU32(); // max length
        in.getBoolean(); // corked
        var fragment = in.getU32();
        for (var i = 0; i < 7; i++) {
            in.getBoolean();
        }
        in.getBoolean(); // peak detect
        var adjustLatency = in.getBoolean();
        var properties = in.getProplist();
        var directOnInput = in.getIndex();
        in.getBoolean(); // early requests
        in.getBoolean();
        in.getBoolean();
        var formats = in.getU8();
        var volume = in.getCVolume();
        for (var i = 0; i < 5; i++) {
            in.getBoolean();
        }
        if (formats != 0 || !volume.equals(ChannelVolumes.uniform(channels, ChannelVolumes.NORM))) {
            throw new IllegalStateException("formats " + formats + ", volume " + volume);
        }
        if (in.hasMore()) {
            throw new IllegalStateException("trailing arguments");
        }
        return spec + " map=" + channels + " source=" + source + " fragment=" + fragment + " adjust=" + adjustLatency + " "
                + properties.get("application.name") + " stream=" + directOnInput;
    }

    @Test
    void recordsAndHandsOverTheAudio() throws Exception {
        var requests = new LinkedBlockingQueue<String>();
        server.on(Command.CREATE_RECORD_STREAM, (in, out) -> {
            requests.add(readRecordRequest(in));
            out.putU32(3).putU32(41); // channel, source output index
        });
        var received = new LinkedBlockingQueue<Float>();

        var stream = connect().record(RecordRequest.source("@DEFAULT_MONITOR@", SampleSpec.float32Mono(8000), Duration.ofMillis(20),
                Map.of("application.name", "meter")), data -> {
            while (data.remaining() >= Float.BYTES) {
                received.add(data.getFloat());
            }
        });
        var audio = java.nio.ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN).putFloat(0.25f).putFloat(-0.5f).array();
        server.data(3, audio);
        server.data(9, audio); // another stream's channel

        assertEquals("SampleSpec[format=5, channels=1, rate=8000] map=1 source=@DEFAULT_MONITOR@ fragment=640 adjust=true meter stream=-1",
                requests.poll(2, TimeUnit.SECONDS));
        assertEquals(41, stream.index());
        assertEquals(0.25f, received.poll(2, TimeUnit.SECONDS));
        assertEquals(-0.5f, received.poll(2, TimeUnit.SECONDS));
        assertNull(received.poll(200, TimeUnit.MILLISECONDS), "audio for another channel is not this recording's");
    }

    @Test
    void monitorsOneStream() throws Exception {
        var requests = new LinkedBlockingQueue<String>();
        server.on(Command.CREATE_RECORD_STREAM, (in, out) -> {
            requests.add(readRecordRequest(in));
            out.putU32(0).putU32(1);
        });

        connect().record(RecordRequest.stream(12, SampleSpec.float32Mono(8000), Duration.ofMillis(20), Map.of()), data -> {
        });

        assertTrue(requests.poll(2, TimeUnit.SECONDS).endsWith("source=null fragment=640 adjust=true null stream=12"));
    }

    @Test
    void aRecordingEndsWhenTheServerKillsIt() throws Exception {
        server.on(Command.CREATE_RECORD_STREAM, (in, out) -> out.putU32(3).putU32(41));
        var stream = connect().record(RecordRequest.source(null, SampleSpec.float32Mono(8000), Duration.ofMillis(20), Map.of()), data -> {
        });

        server.killRecording(3);

        stream.ended().get(2, TimeUnit.SECONDS);
        assertFalse(stream.isOpen());
        assertTrue(client.isOpen());
    }

    @Test
    void closingARecordingDeletesItOnTheServer() throws Exception {
        var deleted = new LinkedBlockingQueue<Long>();
        server.on(Command.CREATE_RECORD_STREAM, (in, out) -> out.putU32(3).putU32(41))
              .on(Command.DELETE_RECORD_STREAM, (in, out) -> deleted.add(in.getU32()));
        var stream = connect().record(RecordRequest.source(null, SampleSpec.float32Mono(8000), Duration.ofMillis(20), Map.of()), data -> {
        });

        stream.close();

        assertEquals(3L, deleted.poll(2, TimeUnit.SECONDS));
        assertFalse(stream.isOpen());
    }

    /**
     * A consumer waiting for a lock that its owner holds while it closes the recording must not keep the reply to that
     * close from being read.
     */
    @Test
    void aBlockedConsumerDoesNotHoldUpReplies() throws Exception {
        var deleted = new LinkedBlockingQueue<Long>();
        server.on(Command.CREATE_RECORD_STREAM, (in, out) -> out.putU32(3).putU32(41))
              .on(Command.DELETE_RECORD_STREAM, (in, out) -> deleted.add(in.getU32()));
        var lock = new Object();
        var stream = connect().record(RecordRequest.source(null, SampleSpec.float32Mono(8000), Duration.ofMillis(20), Map.of()), data -> {
            synchronized (lock) {
                data.position(data.limit());
            }
        });

        synchronized (lock) {
            server.data(3, new byte[8]);
            Thread.sleep(100); // the consumer is now waiting for the lock
            var start = System.nanoTime();
            stream.close();
            assertTrue(System.nanoTime() - start < TIMEOUT.toNanos() / 2, "close waited for the blocked consumer");
        }
        assertEquals(3L, deleted.poll(2, TimeUnit.SECONDS));
    }

    @Test
    void recordingsEndWithTheConnection() throws Exception {
        server.on(Command.CREATE_RECORD_STREAM, (in, out) -> out.putU32(3).putU32(41));
        var stream = connect().record(RecordRequest.source(null, SampleSpec.float32Mono(8000), Duration.ofMillis(20), Map.of()), data -> {
        });

        server.disconnect();

        stream.ended().get(2, TimeUnit.SECONDS);
    }

    @Test
    void nullStringsRoundTrip() {
        var reader = new TagReader(new TagWriter().putString(null).putString("ü").toByteArray());
        assertNull(reader.getString());
        assertEquals("ü", reader.getString());
    }

    @Test
    void aMismatchedTagIsAProtocolError() {
        var reader = new TagReader(new TagWriter().putString("x").toByteArray());
        assertThrows(PulseProtocolException.class, reader::getU32);
    }
}
