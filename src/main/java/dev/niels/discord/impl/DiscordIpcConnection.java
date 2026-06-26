package dev.niels.discord.impl;

import java.io.IOException;

/**
 * Low-level Discord IPC transport: a duplex byte channel that frames each message as an 8-byte
 * little-endian header (opcode, length) followed by a UTF-8 JSON body. Platform implementations back
 * this with a Windows named pipe ({@code \\.\pipe\discord-ipc-N}) or a Unix domain socket — both pure
 * JDK, no JNI, so the GraalVM native image needs no extra reflection for the transport.
 */
public interface DiscordIpcConnection extends AutoCloseable {
    /** Writes one framed message. Thread-safe with respect to other writers. */
    void writeFrame(int opcode, byte[] payload) throws IOException;

    /** Blocks the calling (read) thread until a complete frame has been read. */
    Frame readFrame() throws IOException;

    boolean isOpen();

    @Override
    void close();

    record Frame(int opcode, byte[] body) {
    }
}
