package dev.niels.wavelink.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Guards the one-write-at-a-time rule of {@link WebSocket}: {@code sendText} rejects a message issued
 * while a previous write is still in flight, failing with {@code IllegalStateException("Send pending")}
 * and never putting it on the wire.
 *
 * <p>Two paths issue messages in bursts and so depend on the client's send queue. The post-connect
 * handshake asks for input devices, output devices, channels, mixes and the focus subscription in one
 * go: drop any of them and the handshake never completes, which leaves the connection open but
 * uninitialised — reconnected on the next health check, forever. Commands burst the same way when a
 * dial is turned, where a dropped write is a movement Wave Link never hears about.
 */
class WaveLinkSendQueueTest {
    private final ObjectMapper mapper = new ObjectMapper();

    /** The listener only needs a client for the liveness hooks and the listener fan-out. */
    private static final class TestClient extends WaveLinkClientImpl {
        TestClient() {
            super(false);
        }
    }

    /**
     * A socket with the JDK's send contract: one text write in flight at a time, any other rejected
     * until the transport reports the previous one done. Writes finish only when the test says so, so
     * the race the real transport wins or loses by microseconds is deterministic here.
     */
    private static final class OneWriteAtATimeSocket implements WebSocket {
        private final AtomicBoolean writeInFlight = new AtomicBoolean();
        private final Deque<CompletableFuture<WebSocket>> inFlight = new ArrayDeque<>();
        private final List<String> written = new ArrayList<>();
        private int rejected;

        @Override
        public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            if (!writeInFlight.compareAndSet(false, true)) {
                rejected++;
                return CompletableFuture.failedFuture(new IllegalStateException("Send pending"));
            }
            written.add(data.toString());
            var write = new CompletableFuture<WebSocket>();
            inFlight.add(write);
            return write;
        }

        /** Reports the oldest write done, as the transport does once its bytes are on the wire. */
        boolean finishOldestWrite() {
            var write = inFlight.poll();
            if (write == null) {
                return false;
            }
            writeInFlight.set(false);
            write.complete(this);
            return true;
        }

        @Override
        public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendPing(ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendPong(ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public void request(long n) {
            // no flow control in the fake: every message the test feeds is delivered
        }

        @Override
        public String getSubprotocol() {
            return "";
        }

        @Override
        public boolean isOutputClosed() {
            return false;
        }

        @Override
        public boolean isInputClosed() {
            return false;
        }

        @Override
        public void abort() {
            // nothing to release
        }
    }

    /**
     * Drives the exchange to a standstill: finish the oldest write, then answer everything Wave Link has
     * been sent but not yet replied to, until the handshake completes or no progress is left to make.
     */
    private void pumpUntilInitialised(WaveLinkListener listener, OneWriteAtATimeSocket socket, TestClient client) throws Exception {
        var answered = 0;
        for (var round = 0; round < 50 && !client.isInitialized(); round++) {
            socket.finishOldestWrite();
            while (answered < socket.written.size()) {
                listener.onText(socket, responseTo(socket.written.get(answered++)), true);
            }
        }
    }

    @Test
    void handshakeSendsEveryRequestAndCompletes() throws Exception {
        var client = new TestClient();
        var listener = new WaveLinkListener(client);
        var socket = new OneWriteAtATimeSocket();

        listener.onOpen(socket);
        pumpUntilInitialised(listener, socket, client);

        assertEquals(0, socket.rejected, "no request may be issued while another write is in flight");
        assertEquals(List.of("getApplicationInfo", "getInputDevices", "getOutputDevices", "getChannels", "getMixes", "setSubscription"),
                socket.written.stream().map(this::methodOf).toList(),
                "every handshake request must reach the wire");
        assertTrue(client.isInitialized(), "the post-connect handshake must complete");
    }

    @Test
    void aBurstOfCommandsAllReachesTheWire() throws Exception {
        var client = new TestClient();
        var listener = new WaveLinkListener(client);
        var socket = new OneWriteAtATimeSocket();
        listener.onOpen(socket);
        socket.finishOldestWrite(); // let the handshake's first write clear so the burst starts from idle

        // A dial being turned produces a rapid run of setChannel commands, one per movement.
        for (var i = 0; i < 5; i++) {
            listener.sendExpectingResult(new dev.niels.wavelink.impl.rpc.WaveLinkSetChannelCommand());
        }
        while (socket.finishOldestWrite()) {
            // drain the queue: each finished write releases the next
        }

        assertEquals(0, socket.rejected, "a command burst must not overlap writes");
        assertEquals(5, socket.written.stream().filter(m -> "setChannel".equals(methodOf(m))).count(),
                "every command in the burst must reach the wire");
    }

    private String methodOf(String sent) {
        try {
            return mapper.readTree(sent).get("method").asText();
        } catch (Exception e) {
            throw new IllegalStateException("not a JSON-RPC message: " + sent, e);
        }
    }

    /** The reply Wave Link would send for a request, keyed on its method. */
    private String responseTo(String sent) throws Exception {
        var node = mapper.readTree(sent);
        var result = switch (node.get("method").asText()) {
            case "getApplicationInfo" -> """
                    {"appID":"EWL","operatingSystem":"windows","name":"Elgato Wave Link","version":"3.2.8.3928","build":"3928","interfaceRevision":"2"}""";
            case "getInputDevices" -> "{\"inputDevices\":[]}";
            case "getOutputDevices" -> "{\"mainOutput\":{\"outputDeviceId\":\"main\"},\"outputDevices\":[]}";
            case "getChannels" -> "{\"channels\":[]}";
            case "getMixes" -> "{\"mixes\":[]}";
            case "setSubscription" -> "{\"focusedAppChanged\":{\"isEnabled\":true}}";
            default -> "{}";
        };
        return "{\"jsonrpc\":\"2.0\",\"id\":" + node.get("id").asLong() + ",\"result\":" + result + "}";
    }
}
