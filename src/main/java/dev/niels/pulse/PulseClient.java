package dev.niels.pulse;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import dev.niels.pulse.model.DeviceInfo;
import dev.niels.pulse.model.ServerInfo;
import dev.niels.pulse.model.StreamInfo;
import dev.niels.pulse.model.SubscriptionEvent;
import dev.niels.pulse.model.SubscriptionEvent.Facility;
import lombok.extern.log4j.Log4j2;

/**
 * A client for the PulseAudio native protocol over its Unix socket — the protocol {@code libpulse} and {@code pactl}
 * speak, served by PulseAudio itself and by PipeWire's {@code pipewire-pulse}. One connection carries any number of
 * concurrent requests (matched to their replies by tag) and the subscription events; it uses no shared memory and
 * creates no streams.
 * <p>
 * Replies are read on a thread of the client's own; subscription events are handed to the listener on another, so a
 * listener may make requests of its own.
 */
@Log4j2
public final class PulseClient implements Closeable {
    /**
     * The protocol version asked for (PulseAudio 12). The server answers in the layout of the lower of this and its
     * own, so newer servers never send fields {@link Replies} does not know.
     */
    static final int PROTOCOL_VERSION = 32;
    static final int COOKIE_LENGTH = 256;
    private static final int HEADER_LENGTH = 20;
    private static final int CONTROL_CHANNEL = -1;
    private static final int MAX_PACKET = 16 * 1024 * 1024;
    private static final long INVALID_INDEX = 0xFFFFFFFFL;

    private final SocketChannel channel;
    private final Duration timeout;
    private final Object writeLock = new Object();
    private final AtomicInteger nextTag = new AtomicInteger();
    private final Map<Integer, CompletableFuture<TagReader>> pending = new ConcurrentHashMap<>();
    private final CompletableFuture<Throwable> closed = new CompletableFuture<>();
    private final ExecutorService events = Executors.newSingleThreadExecutor(r -> {
        var t = new Thread(r, "pulse-events");
        t.setDaemon(true);
        return t;
    });
    private volatile Consumer<SubscriptionEvent> listener = e -> {
    };
    private int version;
    private int serverVersion;

    private PulseClient(SocketChannel channel, Duration timeout) {
        this.channel = channel;
        this.timeout = timeout;
    }

    /**
     * Connects to the server at {@code socket}, authenticates with {@code cookie} (PipeWire ignores it; PulseAudio needs
     * the user's cookie file) and announces the client under {@code properties} ({@code application.name} and the
     * like).
     *
     * @param timeout how long any request, the handshake included, may wait for its reply
     */
    public static PulseClient connect(Path socket, byte[] cookie, Map<String, String> properties, Duration timeout) throws IOException {
        var channel = SocketChannel.open(UnixDomainSocketAddress.of(socket));
        var client = new PulseClient(channel, timeout);
        try {
            client.start();
            client.handshake(cookie, properties);
            return client;
        } catch (RuntimeException e) {
            client.close();
            throw e;
        }
    }

    private void start() {
        var reader = new Thread(this::readLoop, "pulse-reader");
        reader.setDaemon(true);
        reader.start();
    }

    private void handshake(byte[] cookie, Map<String, String> properties) {
        var paddedCookie = new byte[COOKIE_LENGTH];
        System.arraycopy(cookie, 0, paddedCookie, 0, Math.min(cookie.length, COOKIE_LENGTH));
        var auth = request(Command.AUTH, w -> w.putU32(PROTOCOL_VERSION).putArbitrary(paddedCookie));
        // The high bits carry shared-memory capability flags.
        serverVersion = (int) (auth.getU32() & 0xFFFF);
        version = Math.min(PROTOCOL_VERSION, serverVersion);
        if (version < 13) {
            throw new PulseException(17, "Server speaks protocol " + serverVersion + "; at least 13 is needed");
        }
        request(Command.SET_CLIENT_NAME, w -> w.putProplist(properties));
    }

    /** The protocol version both sides speak, which decides the layout of every reply. */
    public int version() {
        return version;
    }

    public int serverVersion() {
        return serverVersion;
    }

    public boolean isOpen() {
        return !closed.isDone();
    }

    /** Completes, with the reason, once the connection is gone, whether the server dropped it or {@link #close()} did. */
    public CompletableFuture<Throwable> closed() {
        return closed;
    }

    public ServerInfo serverInfo() {
        return Replies.serverInfo(request(Command.GET_SERVER_INFO, w -> {
        }));
    }

    public List<DeviceInfo> sinks() {
        var reader = request(Command.GET_SINK_INFO_LIST, w -> {
        });
        return Replies.list(reader, r -> Replies.device(r, version, true));
    }

    public List<DeviceInfo> sources() {
        var reader = request(Command.GET_SOURCE_INFO_LIST, w -> {
        });
        return Replies.list(reader, r -> Replies.device(r, version, false));
    }

    /** The sink with this index, or with {@code ref}'s name ({@code @DEFAULT_SINK@} names the default). */
    public DeviceInfo sink(DeviceRef ref) {
        return Replies.device(request(Command.GET_SINK_INFO, ref::write), version, true);
    }

    public DeviceInfo source(DeviceRef ref) {
        return Replies.device(request(Command.GET_SOURCE_INFO, ref::write), version, false);
    }

    /** The playback streams. */
    public List<StreamInfo> sinkInputs() {
        var reader = request(Command.GET_SINK_INPUT_INFO_LIST, w -> {
        });
        return Replies.list(reader, r -> Replies.sinkInput(r, version));
    }

    /** The recording streams. */
    public List<StreamInfo> sourceOutputs() {
        var reader = request(Command.GET_SOURCE_OUTPUT_INFO_LIST, w -> {
        });
        return Replies.list(reader, r -> Replies.sourceOutput(r, version));
    }

    public void setSinkVolume(DeviceRef ref, ChannelVolumes volume) {
        request(Command.SET_SINK_VOLUME, w -> ref.write(w).putCVolume(volume));
    }

    public void setSourceVolume(DeviceRef ref, ChannelVolumes volume) {
        request(Command.SET_SOURCE_VOLUME, w -> ref.write(w).putCVolume(volume));
    }

    public void setSinkMute(DeviceRef ref, boolean mute) {
        request(Command.SET_SINK_MUTE, w -> ref.write(w).putBoolean(mute));
    }

    public void setSourceMute(DeviceRef ref, boolean mute) {
        request(Command.SET_SOURCE_MUTE, w -> ref.write(w).putBoolean(mute));
    }

    public void setSinkInputVolume(int index, ChannelVolumes volume) {
        request(Command.SET_SINK_INPUT_VOLUME, w -> w.putU32(index).putCVolume(volume));
    }

    public void setSinkInputMute(int index, boolean mute) {
        request(Command.SET_SINK_INPUT_MUTE, w -> w.putU32(index).putBoolean(mute));
    }

    public void setDefaultSink(String name) {
        request(Command.SET_DEFAULT_SINK, w -> w.putString(name));
    }

    public void setDefaultSource(String name) {
        request(Command.SET_DEFAULT_SOURCE, w -> w.putString(name));
    }

    /** Moves a playback stream to the sink named {@code sinkName}. */
    public void moveSinkInput(int index, String sinkName) {
        request(Command.MOVE_SINK_INPUT, w -> w.putU32(index).putU32(INVALID_INDEX).putString(sinkName));
    }

    /**
     * Asks for the events of {@code facilities} and hands them to {@code listener} from then on, one at a time and in
     * the order the server sent them. A later call replaces both.
     */
    public void subscribe(Set<Facility> facilities, Consumer<SubscriptionEvent> listener) {
        this.listener = listener;
        var mask = facilities.stream().mapToInt(Facility::maskBit).reduce(0, (a, b) -> a | b);
        request(Command.SUBSCRIBE, w -> w.putU32(mask));
    }

    /**
     * Sends a command and waits for its reply, which is returned positioned after the command and tag.
     *
     * @throws PulseTimeoutException when no reply comes within the client's timeout
     * @throws PulseException        when the server refuses the command ({@link PulseException#isServerError()}) or the
     *                               connection fails
     */
    TagReader request(int command, Consumer<TagWriter> arguments) {
        var tag = nextTag.getAndUpdate(t -> (t + 1) & 0x7FFFFFFF);
        var reply = new CompletableFuture<TagReader>();
        pending.put(tag, reply);
        try {
            if (!isOpen()) {
                throw new PulseException("Connection closed", closed.getNow(null));
            }
            var writer = new TagWriter().putU32(command).putU32(tag);
            arguments.accept(writer);
            send(writer.toByteArray());
            return reply.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new PulseTimeoutException("No reply to command " + command + " within " + timeout.toMillis() + "ms");
        } catch (ExecutionException e) {
            throw e.getCause() instanceof PulseException pe ? pe : new PulseException("Request failed", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PulseTimeoutException("Interrupted while waiting for command " + command);
        } catch (IOException e) {
            fail(e);
            throw new PulseException("Unable to send command " + command, e);
        } finally {
            pending.remove(tag);
        }
    }

    private void send(byte[] payload) throws IOException {
        var header = ByteBuffer.allocate(HEADER_LENGTH);
        header.putInt(payload.length).putInt(CONTROL_CHANNEL).putInt(0).putInt(0).putInt(0).flip();
        var body = ByteBuffer.wrap(payload);
        synchronized (writeLock) {
            while (header.hasRemaining() || body.hasRemaining()) {
                channel.write(new ByteBuffer[] { header, body });
            }
        }
    }

    private void readLoop() {
        var header = ByteBuffer.allocate(HEADER_LENGTH);
        try {
            while (isOpen()) {
                header.clear();
                readFully(header);
                header.flip();
                var length = header.getInt();
                var channelId = header.getInt();
                if (length < 0 || length > MAX_PACKET) {
                    throw new PulseProtocolException("Packet of " + Integer.toUnsignedString(length) + " bytes");
                }
                var payload = ByteBuffer.allocate(length);
                readFully(payload);
                // Anything not on the control channel is stream audio, and this client has no streams.
                if (channelId == CONTROL_CHANNEL) {
                    dispatch(new TagReader(payload.array()));
                }
            }
        } catch (IOException | RuntimeException e) {
            fail(e);
        }
    }

    private void readFully(ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
                throw new EOFException("The server closed the connection");
            }
        }
    }

    private void dispatch(TagReader packet) {
        var command = (int) packet.getU32();
        var tag = (int) packet.getU32();
        if (command == Command.SUBSCRIBE_EVENT) {
            var event = SubscriptionEvent.decode(packet.getU32(), packet.getIndex());
            events.execute(() -> deliver(event));
            return;
        }
        var reply = pending.get(tag);
        if (reply == null) {
            // A server-initiated command, or the reply to a request that already timed out.
            return;
        }
        if (command == Command.REPLY) {
            reply.complete(packet);
        } else if (command == Command.ERROR) {
            var code = (int) packet.getU32();
            reply.completeExceptionally(new PulseException(code, PulseException.describe(code)));
        } else {
            reply.completeExceptionally(new PulseProtocolException("Unexpected command " + command + " in reply to tag " + tag));
        }
    }

    private void deliver(SubscriptionEvent event) {
        try {
            listener.accept(event);
        } catch (RuntimeException e) {
            log.warn("Subscription listener failed on {}", event, e);
        }
    }

    private void fail(Throwable reason) {
        if (!closed.complete(reason)) {
            return;
        }
        if (reason instanceof IOException || reason instanceof PulseProtocolException) {
            log.debug("Pulse connection lost: {}", reason.toString());
        }
        closeChannel();
        var error = new PulseException("Connection closed", reason);
        pending.values().forEach(f -> f.completeExceptionally(error));
        events.shutdown();
    }

    private void closeChannel() {
        try {
            channel.close();
        } catch (IOException e) {
            log.debug("Unable to close the pulse socket", e);
        }
    }

    @Override
    public void close() {
        fail(new EOFException("Closed by the client"));
    }

    /**
     * A sink or source by index, or by name. A name may be {@code @DEFAULT_SINK@} / {@code @DEFAULT_SOURCE@}, which the
     * server resolves to the current default.
     */
    public record DeviceRef(long index, @Nullable String name) {
        public static final DeviceRef DEFAULT_SINK = byName("@DEFAULT_SINK@");
        public static final DeviceRef DEFAULT_SOURCE = byName("@DEFAULT_SOURCE@");

        public static DeviceRef byIndex(int index) {
            return new DeviceRef(index, null);
        }

        public static DeviceRef byName(String name) {
            return new DeviceRef(INVALID_INDEX, name);
        }

        TagWriter write(TagWriter writer) {
            return writer.putU32(index).putString(name);
        }
    }

    /** Command numbers ({@code pulsecore/native-common.h}). */
    static final class Command {
        static final int ERROR = 0;
        static final int REPLY = 2;
        static final int AUTH = 8;
        static final int SET_CLIENT_NAME = 9;
        static final int GET_SERVER_INFO = 20;
        static final int GET_SINK_INFO = 21;
        static final int GET_SINK_INFO_LIST = 22;
        static final int GET_SOURCE_INFO = 23;
        static final int GET_SOURCE_INFO_LIST = 24;
        static final int GET_SINK_INPUT_INFO_LIST = 30;
        static final int GET_SOURCE_OUTPUT_INFO_LIST = 32;
        static final int SUBSCRIBE = 35;
        static final int SET_SINK_VOLUME = 36;
        static final int SET_SINK_INPUT_VOLUME = 37;
        static final int SET_SOURCE_VOLUME = 38;
        static final int SET_SINK_MUTE = 39;
        static final int SET_SOURCE_MUTE = 40;
        static final int SET_DEFAULT_SINK = 44;
        static final int SET_DEFAULT_SOURCE = 45;
        static final int SUBSCRIBE_EVENT = 66;
        static final int MOVE_SINK_INPUT = 67;
        static final int SET_SINK_INPUT_MUTE = 69;

        private Command() {
        }
    }
}
