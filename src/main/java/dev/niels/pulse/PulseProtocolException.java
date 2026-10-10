package dev.niels.pulse;

/** A packet that does not have the shape the protocol says it has; the connection cannot be trusted after one. */
public class PulseProtocolException extends PulseException {
    public PulseProtocolException(String message) {
        super(message, null);
    }
}
