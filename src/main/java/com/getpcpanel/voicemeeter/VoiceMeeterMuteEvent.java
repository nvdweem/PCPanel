package com.getpcpanel.voicemeeter;

import com.getpcpanel.voicemeeter.Voicemeeter.ControlType;

public record VoiceMeeterMuteEvent(ControlType ct, int idx, boolean muted) {
}
