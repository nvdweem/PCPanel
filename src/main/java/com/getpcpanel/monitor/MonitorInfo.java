package com.getpcpanel.monitor;

public record MonitorInfo(String id, String name) {
    @Override
    public String toString() {
        if (name == null || name.isBlank()) {
            return id == null ? "Unknown Monitor" : id;
        }
        return name;
    }
}
