import { WebSocket, WebSocketServer } from 'ws';

const SERVER_PORT = 1884;
const TARGET_PORT = 1885;
const TARGET_HOST = 'localhost';

const CONNECT_HEADERS = {
    origin: 'streamdeck://',
};

log(`Starting WebSocket proxy server on port ${SERVER_PORT}`);
log(`Proxying connections to ${TARGET_HOST}:${TARGET_PORT}`);

const wss = new WebSocketServer({port: SERVER_PORT});

wss.on('listening', () => {
    log(`WebSocket proxy server listening on port ${SERVER_PORT}`);
});

wss.on('error', (error) => {
    console.error('WebSocket server error:', error);
});

wss.on('connection', (clientSocket: WebSocket, request) => {
    const clientId = `${request.socket.remoteAddress}:${request.socket.remotePort}`;
    log(`StreamDeck connected: ${clientId}`);

    // Buffer for messages received before target connection is established
    const messageBuffer: Array<{ data: any; isBinary: boolean }> = [];
    let targetConnected = false;

    // Connect to the target server with forwarded headers
    const targetSocket = new WebSocket(`ws://${TARGET_HOST}:${TARGET_PORT}`, CONNECT_HEADERS);

    targetSocket.on('open', () => {
        log(`Connected to WaveLink ${TARGET_HOST}:${TARGET_PORT} for StreamDeck ${clientId}`);
        targetConnected = true;

        // Send all buffered messages
        if (messageBuffer.length > 0) {
            log(`Sending ${messageBuffer.length} buffered message(s) to WaveLink`);
            messageBuffer.forEach(({data, isBinary}) => {
                targetSocket.send(data, {binary: isBinary});
            });
            messageBuffer.length = 0; // Clear buffer
        }
    });

    targetSocket.on('error', (error) => {
        console.error(`[${new Date().toISOString()}] Target server error for StreamDeck ${clientId}:`, error.message);
        clientSocket.close();
    });

    targetSocket.on('close', () => {
        log(`WaveLink connection closed for StreamDeck ${clientId}`);
        clientSocket.close();
    });

    // Proxy messages from target to client
    targetSocket.on('message', (data, isBinary) => {
        const dataSize = Buffer.isBuffer(data) ? data.length : data instanceof ArrayBuffer ? data.byteLength : 0;
        const message = isBinary ? `<binary data: ${dataSize} bytes>` : data.toString();
        log(`WaveLink -> StreamDeck (${clientId}): ${message}`);

        if (clientSocket.readyState === WebSocket.OPEN) {
            clientSocket.send(data, {binary: isBinary});
        }
    });

    // Proxy messages from client to target
    clientSocket.on('message', (data, isBinary) => {
        const dataSize = Buffer.isBuffer(data) ? data.length : data instanceof ArrayBuffer ? data.byteLength : 0;
        const message = isBinary ? `<binary data: ${dataSize} bytes>` : data.toString();

        if (targetConnected && targetSocket.readyState === WebSocket.OPEN) {
            log(`StreamDeck (${clientId}) -> WaveLink: ${message}`);
            targetSocket.send(data, {binary: isBinary});
        } else {
            log(`StreamDeck (${clientId}) -> Buffered: ${message}`);
            messageBuffer.push({data, isBinary});
        }
    });

    clientSocket.on('error', (error) => {
        console.error(`[${new Date().toISOString()}] StreamDeck error (${clientId}):`, error.message);
        targetSocket.close();
    });

    clientSocket.on('close', () => {
        log(`StreamDeck disconnected: ${clientId}`);
        targetSocket.close();
    });
});

log('WebSocket proxy is ready to accept connections');

function log(msg: string, ...attrs: unknown[]) {
    console.log(`${msg}`, ...attrs);
}
