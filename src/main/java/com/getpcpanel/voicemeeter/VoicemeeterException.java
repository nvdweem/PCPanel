package com.getpcpanel.voicemeeter;

import lombok.Getter;

public class VoicemeeterException extends Exception {
    @Getter private final boolean disconnected;

    public VoicemeeterException(String message) {
        super(message);
        disconnected = false;
    }

    public VoicemeeterException(String message, boolean disconnected) {
        super(message);
        this.disconnected = disconnected;
    }
}

