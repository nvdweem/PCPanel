package dev.niels.discord.impl;

import java.io.EOFException;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

/**
 * Linux/macOS transport over the Discord Unix domain socket. Uses the JDK 16+ {@code UnixDomainSocketAddress}
 * + {@code SocketChannel} (pure Java, no junixsocket/JNI). Reads run blocking on the read thread; writes are
 * serialised so concurrent writers can't interleave a frame.
 */
final class UnixSocketConnection implements DiscordIpcConnection {
    private final SocketChannel channel;
    private volatile boolean open = true;
    private final AtomicBoolean closing = new AtomicBoolean(false);

    private UnixSocketConnection(SocketChannel channel) {
        this.channel = channel;
    }

    @Nullable
    static UnixSocketConnection openFirstAvailable() {
        for (Path candidate : DiscordIpcConnections.unixSocketCandidates()) {
            try {
                var ch = SocketChannel.open(StandardProtocolFamily.UNIX);
                ch.connect(UnixDomainSocketAddress.of(candidate));
                ch.configureBlocking(true);
                return new UnixSocketConnection(ch);
            } catch (IOException ignored) {
                // Socket not present / not listening at this path — try the next candidate.
            }
        }
        return null;
    }

    @Override
    public synchronized void writeFrame(int opcode, byte[] payload) throws IOException {
        var buf = ByteBuffer.wrap(DiscordIpcFraming.encode(opcode, payload));
        while (buf.hasRemaining()) {
            channel.write(buf);
        }
    }

    @Override
    public Frame readFrame() throws IOException {
        var head = readFully(ByteBuffer.allocate(8)).order(ByteOrder.LITTLE_ENDIAN);
        var opcode = head.getInt(0);
        var len = head.getInt(4);
        if (len < 0) {
            throw new IOException("Negative Discord frame length " + len);
        }
        var body = readFully(ByteBuffer.allocate(len));
        return new Frame(opcode, body.array());
    }

    private ByteBuffer readFully(ByteBuffer buf) throws IOException {
        while (buf.hasRemaining()) {
            if (channel.read(buf) < 0) {
                throw new EOFException("Discord IPC socket closed");
            }
        }
        return buf.flip();
    }

    @Override
    public boolean isOpen() {
        return open && channel.isOpen();
    }

    @Override
    public void close() {
        open = false;
        // Closing a SocketChannel interrupts a blocking read (unlike the Windows pipe), so this does not
        // stall; the CAS guard just keeps a redundant second close() a harmless no-op, matching the pipe.
        if (closing.compareAndSet(false, true)) {
            try {
                channel.close();
            } catch (IOException ignored) {
                // Already-closed channel can throw; nothing to do.
            }
        }
    }
}
