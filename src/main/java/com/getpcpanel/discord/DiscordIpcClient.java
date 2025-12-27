package com.getpcpanel.discord;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.log4j.Log4j2;

@Log4j2
final class DiscordIpcClient {
    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;
    private static final String DEFAULT_CLIENT_ID = "1454170387217780882";

    private final ObjectMapper mapper;
    private RandomAccessFile pipe;
    private String pipePath;
    private String clientId = DEFAULT_CLIENT_ID;

    DiscordIpcClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    synchronized boolean connect(String overridePath, String overrideClientId) {
        if (isConnected()) {
            return true;
        }
        if (overrideClientId != null && !overrideClientId.isBlank()) {
            clientId = overrideClientId.trim();
        } else {
            clientId = DEFAULT_CLIENT_ID;
        }
        if (overridePath != null && !overridePath.isBlank()) {
            if (openPipe(overridePath)) {
                pipePath = overridePath;
                return handshake();
            }
            return false;
        }
        for (int i = 0; i < 10; i++) {
            var path = "\\\\.\\pipe\\discord-ipc-" + i;
            if (openPipe(path)) {
                pipePath = path;
                if (handshake()) {
                    return true;
                }
                close();
            }
        }
        return false;
    }

    synchronized void close() {
        if (pipe != null) {
            try {
                pipe.close();
            } catch (IOException ignored) {
            }
            pipe = null;
        }
    }

    synchronized boolean isConnected() {
        return pipe != null;
    }

    synchronized JsonNode send(String cmd, ObjectNode args, boolean expectResponse) throws IOException {
        ensureConnected();
        var payload = mapper.createObjectNode();
        payload.put("cmd", cmd);
        payload.set("args", args);
        payload.put("nonce", UUID.randomUUID().toString());
        writeFrame(OP_FRAME, payload);
        if (!expectResponse) {
            return null;
        }
        return readFrame();
    }

    private void ensureConnected() throws IOException {
        if (pipe == null) {
            throw new IOException("Discord IPC not connected");
        }
    }

    private boolean openPipe(String path) {
        try {
            pipe = new RandomAccessFile(path, "rw");
            pipePath = path;
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean handshake() {
        try {
            var payload = mapper.createObjectNode();
            payload.put("v", 1);
            payload.put("client_id", clientId);
            writeFrame(OP_HANDSHAKE, payload);
            var response = readFrame();
            if (response != null && response.has("evt") && "READY".equalsIgnoreCase(response.get("evt").asText())) {
                return true;
            }
            return response != null;
        } catch (IOException e) {
            log.debug("Discord handshake failed on {}: {}", pipePath, e.getMessage());
            close();
            return false;
        }
    }

    private void writeFrame(int op, ObjectNode payload) throws IOException {
        var bytes = mapper.writeValueAsBytes(payload);
        var header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                               .putInt(op)
                               .putInt(bytes.length)
                               .array();
        pipe.write(header);
        pipe.write(bytes);
        pipe.getFD().sync();
    }

    private JsonNode readFrame() throws IOException {
        var header = new byte[8];
        try {
            pipe.readFully(header);
        } catch (IOException e) {
            close();
            throw e;
        }
        var buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        buffer.getInt(); // op
        var len = buffer.getInt();
        if (len <= 0 || len > 1024 * 1024) {
            return null;
        }
        var payload = new byte[len];
        try {
            pipe.readFully(payload);
        } catch (IOException e) {
            close();
            throw e;
        }
        var text = new String(payload, StandardCharsets.UTF_8);
        return mapper.readTree(text);
    }
}
