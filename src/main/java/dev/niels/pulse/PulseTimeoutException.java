package dev.niels.pulse;

/** The server did not answer a request in time. The connection may still be fine. */
public class PulseTimeoutException extends PulseException {
    public PulseTimeoutException(String message) {
        super(message, null);
    }
}
