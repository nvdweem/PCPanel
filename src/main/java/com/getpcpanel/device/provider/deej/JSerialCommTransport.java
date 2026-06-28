package com.getpcpanel.device.provider.deej;

import java.util.List;
import java.util.function.Consumer;

import com.fazecast.jSerialComm.SerialPort;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

/**
 * jSerialComm-backed {@link SerialTransport}. A thin adapter: it opens the port at the requested
 * baud (8 data bits, no parity, 1 stop bit), reads available bytes on a dedicated daemon thread and
 * splits them into lines on {@code '\n'}, handing each raw line to the consumer. All protocol
 * parsing stays in {@code DeejProtocol}.
 *
 * <p>Native-image: {@code com.fazecast.jSerialComm} is {@code --initialize-at-run-time}, the bundled
 * native libs are embedded via {@code resources.includes}, and {@code SerialPort}'s fields/ctor are
 * registered in {@code jni-config.json} (see {@code META-INF/native-image/com.getpcpanel/deej/}).
 */
@Log4j2
@ApplicationScoped
public class JSerialCommTransport implements SerialTransport {
    private static final int READ_TIMEOUT_MS = 100;

    static {
        // Pin jSerialComm to the os.arch-correct native lib BEFORE SerialPort's static initializer runs
        // (it is triggered by the first SerialPort.* call below). Works around the 2.10.2 Windows loader
        // picking the bundled ARM64 DLL on an x86-64 host. This is the only class that touches
        // com.fazecast.jSerialComm, so this static block is the single, earliest entry point.
        JSerialCommArchFix.apply();
    }

    @Override
    public List<PortInfo> listPorts() {
        return StreamEx.of(SerialPort.getCommPorts())
                       .map(p -> new PortInfo(p.getSystemPortName(), p.getDescriptivePortName()))
                       .toList();
    }

    @Override
    public SerialConnection open(String portName, int baud, Consumer<String> onLine, Consumer<Throwable> onError) {
        var port = SerialPort.getCommPort(portName);
        port.setComPortParameters(baud, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, READ_TIMEOUT_MS, 0);
        if (!port.openPort()) {
            throw new IllegalStateException("Unable to open serial port " + portName);
        }
        var conn = new JSerialConnection(port, portName, onLine, onError);
        conn.start();
        return conn;
    }

    private static final class JSerialConnection implements SerialConnection {
        private final SerialPort port;
        private final String portName;
        private final Consumer<String> onLine;
        private final Consumer<Throwable> onError;
        private final Thread readerThread;
        private final StringBuilder lineBuffer = new StringBuilder();
        private volatile boolean running = true;

        private JSerialConnection(SerialPort port, String portName, Consumer<String> onLine, Consumer<Throwable> onError) {
            this.port = port;
            this.portName = portName;
            this.onLine = onLine;
            this.onError = onError;
            readerThread = new Thread(this::readLoop, "deej-serial-" + portName);
            readerThread.setDaemon(true);
        }

        void start() {
            readerThread.start();
        }

        private void readLoop() {
            var buf = new byte[256];
            try {
                while (running && port.isOpen()) {
                    var read = port.readBytes(buf, buf.length);
                    if (read < 0) {
                        if (running) {
                            onError.accept(new IllegalStateException("Serial read error on " + portName));
                        }
                        return;
                    }
                    for (var i = 0; i < read; i++) {
                        var c = (char) (buf[i] & 0xFF);
                        if (c == '\n') {
                            var line = lineBuffer.toString();
                            lineBuffer.setLength(0);
                            // One bad line must never kill the reader; the consumer guards itself,
                            // but defend here too (mirror the HID reader's Throwable guard).
                            try {
                                onLine.accept(line);
                            } catch (Throwable t) {
                                log.error("Error handling serial line from {}", portName, t);
                            }
                        } else if (c != '\r') {
                            lineBuffer.append(c);
                        }
                    }
                }
            } catch (Throwable t) {
                if (running) {
                    onError.accept(t);
                }
            }
        }

        @Override
        public String portName() {
            return portName;
        }

        @Override
        public boolean isOpen() {
            return running && port.isOpen();
        }

        @Override
        public void close() {
            if (!running) {
                return;
            }
            running = false;
            readerThread.interrupt();
            try {
                port.closePort();
            } catch (Exception e) {
                log.debug("Error closing serial port {}", portName, e);
            }
        }
    }
}
