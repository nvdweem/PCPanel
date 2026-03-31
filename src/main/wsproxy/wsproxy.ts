import { WebSocket, WebSocketServer } from 'ws';
import * as fs from 'fs';
import * as util from 'util';

let log_file = fs.createWriteStream(__dirname + '/debug.log', {flags: 'w'});

if (process.argv[2]?.toLowerCase() == 'wavelink') {
    setupWaveLinkProxy();
}

const arg2 = (process.argv[3] ?? 'localhost:1885').split(':');
const LISTEN_PORT = Number(process.argv[2]) || 1884;
const CONNECT_PORT = Number(arg2[1]) || 1885;
const CONNECT_HOST = arg2[0] || 'localhost';
const CONNECT_HEADERS = {
    origin: 'streamdeck://',
};


log(`Starting WebSocket proxy server on port ${LISTEN_PORT}`);
log(`Proxying connections to ${CONNECT_HOST}:${CONNECT_PORT}`);

let isListening = false;
const wss = new WebSocketServer({port: LISTEN_PORT});

wss.on('listening', () => {
    isListening = true;
    log(`WebSocket proxy server listening on port ${LISTEN_PORT}`);
});

wss.on('error', (error) => {
    if (!isListening || (error as any).syscall === 'listen') {
        console.error('Unable to bind WebSocket server to port', LISTEN_PORT, ':', error.message);
        console.error('Please ensure no other application is using this port and try again.');
        process.exit(1);
    }
    console.error('WebSocket server error:', error);
});

wss.on('connection', (clientSocket: WebSocket, request) => {
    const clientId = `${request.socket.remoteAddress}:${request.socket.remotePort}`;
    log(`StreamDeck connected: ${clientId}`);

    // Buffer for messages received before target connection is established
    const messageBuffer: Array<{ data: any; isBinary: boolean }> = [];
    let targetConnected = false;

    // Connect to the target server with forwarded headers
    const targetSocket = new WebSocket(`ws://${CONNECT_HOST}:${CONNECT_PORT}`, CONNECT_HEADERS);

    targetSocket.on('open', () => {
        log(`Connected to WaveLink ${CONNECT_HOST}:${CONNECT_PORT} for StreamDeck ${clientId}`);
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
    log_file.write(util.format.apply(null, arguments) + '\n');
}

function writeTempFile(targetFile: string, originalContent: string, newContent: string) {
    log('Replacing wavelink port file', targetFile);
    fs.writeFileSync(targetFile, newContent, 'utf-8');

    // Restore original file on shutdown
    process.on('exit', () => {
        fs.writeFileSync(targetFile, originalContent, 'utf-8');
        log('Restored original WaveLink configuration file', targetFile);
    });

    process.on('SIGINT', () => {
        log('Received SIGINT, shutting down...');
        process.exit(0);
    });

    process.on('SIGTERM', () => {
        log('Received SIGTERM, shutting down...');
        process.exit(0);
    });
}

function setupWaveLinkProxy() {
    const portFile = process.env.APPDATA?.replace('Roaming', 'Local/Packages/Elgato.WaveLink_g54w8ztgkx496/LocalState/ws-info.json');
    if (portFile) {
        try {
            const originalFileContent = fs.readFileSync(portFile, 'utf-8');
            const content = JSON.parse(originalFileContent);
            const wavelinkPort = content.port;

            content.port = 1883;
            writeTempFile(portFile, originalFileContent, JSON.stringify(content));

            process.argv.length = 4;
            process.argv[2] = '1883';
            process.argv[3] = `localhost:${wavelinkPort}`;
        } catch (error) {
            console.error('Failed to read WaveLink port file:', error);
        }
    }
}
