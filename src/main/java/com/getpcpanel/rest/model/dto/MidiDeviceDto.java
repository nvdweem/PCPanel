package com.getpcpanel.rest.model.dto;

/** A detected MIDI input device for the device-listing UI (MIDI is auto-discovered). */
public record MidiDeviceDto(String id, String name, boolean connected) {
}
