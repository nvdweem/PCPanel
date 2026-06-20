package com.getpcpanel.device.io;

import java.util.List;
import java.util.function.Consumer;

/**
 * A mockable serial-port transport seam. Production code uses a jSerialComm-backed implementation
 * ({@code JSerialCommTransport}); tests use an in-memory fake that feeds canned lines, so the Deej
 * provider can be exercised end-to-end without a real port or any hardware.
 *
 * <p>The transport is a thin adapter: it opens a port at a baud rate (8-N-1), reads available bytes,
 * splits them into newline-delimited lines and hands each raw line to a consumer. All protocol
 * parsing/validation stays in {@code DeejProtocol}.
 */
public interface SerialTransport {
    /** Lists the serial ports currently available on the system. */
    List<PortInfo> listPorts();

    /**
     * Opens {@code portName} at {@code baud} (8 data bits, no parity, 1 stop bit) and delivers each
     * inbound line (terminator stripped or included — the parser tolerates both) to {@code onLine}.
     * Reading happens on the connection's own thread. {@code onError} is invoked once if the read
     * loop fails or the port closes unexpectedly. Returns an open {@link SerialConnection}.
     */
    SerialConnection open(String portName, int baud, Consumer<String> onLine, Consumer<Throwable> onError);

    /** A serial port's identity for the manual add-device UI. */
    record PortInfo(String port, String description) {
    }

    /** An open serial connection. Idempotent {@link #close()}. */
    interface SerialConnection extends AutoCloseable {
        String portName();

        boolean isOpen();

        @Override
        void close();
    }
}
