package dev.niels.pulse;

/** A request the server refused ({@link #getCode()} is its {@code pa_error_code_t}), or a connection that failed. */
public class PulseException extends RuntimeException {
    /** The code of an error that did not come from the server. */
    public static final int NO_SERVER_CODE = -1;
    public static final int ERR_NOENTITY = 5;

    private final int code;

    public PulseException(int code, String message) {
        super(message);
        this.code = code;
    }

    public PulseException(String message, Throwable cause) {
        super(message, cause);
        code = NO_SERVER_CODE;
    }

    public int getCode() {
        return code;
    }

    /** Whether the server answered (and the connection is still fine), as opposed to the connection failing. */
    public boolean isServerError() {
        return code != NO_SERVER_CODE;
    }

    static String describe(int code) {
        return switch (code) {
            case 1 -> "access denied";
            case 2 -> "unknown command";
            case 3 -> "invalid argument";
            case 4 -> "entity exists";
            case ERR_NOENTITY -> "no such entity";
            case 7 -> "protocol error";
            case 9 -> "no authentication key";
            case 17 -> "bad version";
            case 19 -> "not supported";
            default -> "error " + code;
        };
    }
}
