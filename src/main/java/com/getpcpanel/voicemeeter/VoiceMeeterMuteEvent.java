package com.getpcpanel.voicemeeter;

import com.getpcpanel.voicemeeter.Voicemeeter.ButtonType;
import com.getpcpanel.voicemeeter.Voicemeeter.ControlType;

public record VoiceMeeterMuteEvent(ControlType ct, int idx, ButtonType button, boolean state) {
}
