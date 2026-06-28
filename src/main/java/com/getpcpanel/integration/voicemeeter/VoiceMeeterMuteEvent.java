package com.getpcpanel.integration.voicemeeter;

import com.getpcpanel.integration.voicemeeter.Voicemeeter.ButtonType;
import com.getpcpanel.integration.voicemeeter.Voicemeeter.ControlType;

public record VoiceMeeterMuteEvent(ControlType ct, int idx, ButtonType button, boolean state) {
}
