package dev.niels.discord.impl;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

/**
 * Windows transport over the Discord named pipe ({@code \\.\pipe\discord-ipc-N}) using
 * {@link AsynchronousFileChannel} — pure JDK (Windows overlapped I/O under the hood), no JNI.
 *
 * <p>This must NOT use {@code RandomAccessFile}: that is a single <em>synchronous</em> handle, and on a
 * synchronous handle Windows serialises I/O — a blocking {@code readFully} on the read thread starves
 * every concurrent write until the handle frees. On a quiet RPC connection (no incoming frames) that is a
 * hard deadlock: the write waits for the handle, the read waits for a frame, and Discord waits for the
 * write it never receives — so voice commands silently time out. Overlapped I/O lets reads and writes
 * proceed independently, and {@link #close()} cancels a pending read instead of blocking on it. Position is
 * ignored by the OS for a pipe (it is a byte stream), so every read/write passes 0.
 */
final class WindowsPipeConnection implements DiscordIpcConnection {
    private final AsynchronousFileChannel channel;
    private volatile boolean open = true;
    private final AtomicBoolean closing = new AtomicBoolean(false);

    private WindowsPipeConnection(AsynchronousFileChannel channel) {
        this.channel = channel;
    }

    @Nullable
    static WindowsPipeConnection openFirstAvailable() {
        for (var i = 0; i < 10; i++) {
            try {
                var ch = AsynchronousFileChannel.open(Path.of("\\\\.\\pipe\\discord-ipc-" + i),
                        Set.of(StandardOpenOption.READ, StandardOpenOption.WRITE), null);
                return new WindowsPipeConnection(ch);
            } catch (IOException ignored) {
                // No Discord pipe at this index — try the next one.
            }
        }
        return null;
    }

    @Override
    public void writeFrame(int opcode, byte[] payload) throws IOException {
        var buf = ByteBuffer.wrap(DiscordIpcFraming.encode(opcode, payload));
        while (buf.hasRemaining()) {
            await(channel.write(buf, 0), "write"); // overlapped write — runs concurrently with a pending read
        }
    }

    @Override
    public Frame readFrame() throws IOException {
        var head = readFully(8).order(ByteOrder.LITTLE_ENDIAN);
        var opcode = head.getInt(0);
        var len = head.getInt(4);
        if (len < 0) {
            throw new IOException("Negative Discord frame length " + len);
        }
        var body = readFully(len);
        return new Frame(opcode, body.array());
    }

    private ByteBuffer readFully(int n) throws IOException {
        var buf = ByteBuffer.allocate(n);
        while (buf.hasRemaining()) {
            if (await(channel.read(buf, 0), "read") < 0) {
                throw new EOFException("Discord pipe closed");
            }
        }
        return buf.flip();
    }

    /** Blocks the calling (read or writer) thread on one overlapped op, mapping its failure modes to IOException. */
    private static int await(java.util.concurrent.Future<Integer> op, String what) throws IOException {
        try {
            return op.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during Discord pipe " + what, e);
        } catch (ExecutionException e) {
            var cause = e.getCause();
            throw cause instanceof IOException io ? io : new IOException("Discord pipe " + what + " failed", cause);
        }
    }

    @Override
    public boolean isOpen() {
        return open && channel.isOpen();
    }

    @Override
    public void close() {
        open = false;
        // Idempotent: AsynchronousFileChannel.close() cancels pending reads (no blocking), but guard anyway
        // so a redundant second close() from onConnectionDropped is a harmless no-op.
        if (closing.compareAndSet(false, true)) {
            try {
                channel.close();
            } catch (IOException ignored) {
                // Closing a broken pipe can throw; nothing useful to do.
            }
        }
    }
}
