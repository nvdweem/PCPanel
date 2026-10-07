package dev.niels.pulse;

import java.io.Closeable;
import java.io.IOException;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * A server end of the native protocol for tests: it answers the handshake, then each command with the handler
 * registered for it ({@link #on}), records every command it received and can push subscription events. Commands
 * without a handler get an empty reply.
 */
final class FakePulseServer implements Closeable {
    static final int VERSION = 32;

    private final Path socket;
    private final ServerSocketChannel server;
    private final Map<Integer, BiConsumer<TagReader, TagWriter>> handlers = new ConcurrentHashMap<>();
    final List<Integer> received = new CopyOnWriteArrayList<>();
    private volatile SocketChannel client;
    volatile int serverVersion = VERSION;

    FakePulseServer(Path dir) throws IOException {
        socket = dir.resolve("native");
        Files.deleteIfExists(socket);
        server = ServerSocketChannel.open(java.net.StandardProtocolFamily.UNIX);
        server.bind(UnixDomainSocketAddress.of(socket));
        var thread = new Thread(this::serve, "fake-pulse");
        thread.setDaemon(true);
        thread.start();
    }

    Path socket() {
        return socket;
    }

    /** Answers {@code command} by writing the reply's fields (after command and tag) from the request's arguments. */
    FakePulseServer on(int command, BiConsumer<TagReader, TagWriter> handler) {
        handlers.put(command, handler);
        return this;
    }

    /** Answers {@code command} with an error. */
    FakePulseServer fail(int command, int code) {
        return on(command, (in, out) -> {
            throw new ServerError(code);
        });
    }

    /** Never answers {@code command}. */
    FakePulseServer ignore(int command) {
        return on(command, (in, out) -> {
            throw new NoReply();
        });
    }

    void event(long type, int index) throws IOException {
        send(new TagWriter().putU32(PulseClient.Command.SUBSCRIBE_EVENT).putU32(0xFFFFFFFFL).putU32(type).putU32(index).toByteArray());
    }

    /** Drops the connection, as a restarting server does. */
    void disconnect() throws IOException {
        client.close();
    }

    private void serve() {
        try {
            client = server.accept();
            var header = ByteBuffer.allocate(20);
            while (true) {
                header.clear();
                readFully(header);
                header.flip();
                var payload = ByteBuffer.allocate(header.getInt());
                readFully(payload);
                handle(new TagReader(payload.array()));
            }
        } catch (IOException e) {
            // The client went away.
        }
    }

    private void handle(TagReader request) throws IOException {
        var command = (int) request.getU32();
        var tag = request.getU32();
        received.add(command);
        var reply = new TagWriter().putU32(PulseClient.Command.REPLY).putU32(tag);
        try {
            if (command == PulseClient.Command.AUTH) {
                request.getU32();
                request.getArbitrary();
                reply.putU32(serverVersion);
            } else if (command == PulseClient.Command.SET_CLIENT_NAME) {
                request.getProplist();
                reply.putU32(7);
            } else {
                handlers.getOrDefault(command, (in, out) -> {
                }).accept(request, reply);
            }
            send(reply.toByteArray());
        } catch (ServerError e) {
            send(new TagWriter().putU32(PulseClient.Command.ERROR).putU32(tag).putU32(e.code).toByteArray());
        } catch (NoReply e) {
            // Leave the client waiting.
        }
    }

    private synchronized void send(byte[] payload) throws IOException {
        var header = ByteBuffer.allocate(20).putInt(payload.length).putInt(-1).putInt(0).putInt(0).putInt(0).flip();
        var body = ByteBuffer.wrap(payload);
        while (header.hasRemaining() || body.hasRemaining()) {
            client.write(new ByteBuffer[] { header, body });
        }
    }

    private void readFully(ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            if (client.read(buffer) < 0) {
                throw new IOException("closed");
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (client != null) {
            client.close();
        }
        server.close();
        Files.deleteIfExists(socket);
    }

    private static final class ServerError extends RuntimeException {
        final int code;

        ServerError(int code) {
            this.code = code;
        }
    }

    private static final class NoReply extends RuntimeException {
    }
}
