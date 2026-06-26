package dev.niels.discord.impl;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.annotation.Nullable;

/**
 * Windows transport over the Discord named pipe. {@code RandomAccessFile} opens {@code \\.\pipe\discord-ipc-N}
 * directly (pure Java, no JNI); a blocking {@code readFully} on the read thread reads frames while writes
 * happen from other threads — the duplex pipe handles concurrent read/write.
 */
final class WindowsPipeConnection implements DiscordIpcConnection {
    private final RandomAccessFile pipe;
    private volatile boolean open = true;

    private WindowsPipeConnection(RandomAccessFile pipe) {
        this.pipe = pipe;
    }

    @Nullable
    static WindowsPipeConnection openFirstAvailable() {
        for (var i = 0; i < 10; i++) {
            try {
                return new WindowsPipeConnection(new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw"));
            } catch (FileNotFoundException ignored) {
                // No Discord pipe at this index — try the next one.
            }
        }
        return null;
    }

    @Override
    public synchronized void writeFrame(int opcode, byte[] payload) throws IOException {
        pipe.write(DiscordIpcFraming.encode(opcode, payload));
    }

    @Override
    public Frame readFrame() throws IOException {
        var head = new byte[8];
        pipe.readFully(head);
        var h = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        var opcode = h.getInt();
        var len = h.getInt();
        if (len < 0) {
            throw new IOException("Negative Discord frame length " + len);
        }
        var body = new byte[len];
        pipe.readFully(body);
        return new Frame(opcode, body);
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public synchronized void close() {
        open = false;
        try {
            pipe.close();
        } catch (IOException ignored) {
            // Closing a broken pipe can throw; nothing useful to do.
        }
    }
}
