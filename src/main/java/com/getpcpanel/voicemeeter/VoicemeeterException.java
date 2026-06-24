/*
 * Adapted from Voicemeeter-JNA-Interface by mattco98 — https://github.com/mattco98/Voicemeeter-JNA-Interface
 */
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
