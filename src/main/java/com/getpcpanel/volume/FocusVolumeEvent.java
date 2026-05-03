package com.getpcpanel.volume;

public record FocusVolumeEvent(FocusVolumeTarget target, float volume) {
    public record FocusVolumeTarget(FocusVolumeEventType type, String value) {}
}
