package com.getpcpanel.commands;

public record DeviceSet(String name,
                        String mediaPlayback,
                        String mediaRecord,
                        String communicationPlayback,
                        String communicationRecord) {
}
