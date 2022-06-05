package com.getpcpanel.cpp;

public record AudioSessionEvent(AudioSession session, Type eventType) {
    public enum Type {
        ADDED,
        REMOVED,
        CHANGED
    }
}
