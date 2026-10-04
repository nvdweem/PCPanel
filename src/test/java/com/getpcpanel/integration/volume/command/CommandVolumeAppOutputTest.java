package com.getpcpanel.integration.volume.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.AppLikeMapper;
import com.getpcpanel.commands.command.Command;

class CommandVolumeAppOutputTest {
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = AppLikeMapper.build();
    }

    @Test
    void roundTripsAppsAndDevice() throws Exception {
        var json = "{\"_type\":\"volume.app-output\",\"processName\":[\"spotify.exe\"],\"device\":\"{0.0.0.00000000}.{abc}\"}";

        var read = assertInstanceOf(CommandVolumeAppOutput.class, mapper.readValue(json, Command.class));
        var again = assertInstanceOf(CommandVolumeAppOutput.class, mapper.readValue(mapper.writeValueAsString(read), Command.class));

        assertEquals(List.of("spotify.exe"), again.getProcessName());
        assertEquals("{0.0.0.00000000}.{abc}", again.getDevice());
    }

    @Test
    void blankDeviceIsTheDefault() throws Exception {
        var json = "{\"_type\":\"volume.app-output\",\"processName\":[\"spotify.exe\"],\"device\":\"  \"}";

        var read = assertInstanceOf(CommandVolumeAppOutput.class, mapper.readValue(json, Command.class));

        assertNull(read.getDevice());
    }

    @Test
    void labelNamesTheAppsAndTheDevice() {
        assertEquals("spotify.exe, firefox → Headphones", CommandVolumeAppOutput.label(List.of("spotify.exe", "firefox"), "Headphones"));
        assertEquals("spotify.exe → Default", CommandVolumeAppOutput.label(List.of("spotify.exe"), null));
    }
}
