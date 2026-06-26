package dev.niels.discord.impl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Encodes a Discord IPC frame: uint32 opcode + uint32 length (both little-endian) + body, as one buffer. */
final class DiscordIpcFraming {
    private DiscordIpcFraming() {
    }

    static byte[] encode(int opcode, byte[] body) {
        return ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN)
                         .putInt(opcode).putInt(body.length).put(body).array();
    }
}
