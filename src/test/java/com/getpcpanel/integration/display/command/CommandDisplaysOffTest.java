package com.getpcpanel.integration.display.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.AppLikeMapper;
import com.getpcpanel.commands.command.Command;

class CommandDisplaysOffTest {
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = AppLikeMapper.build();
    }

    @Test
    void anOlderSaveMeansAllDisplays() throws Exception {
        var command = assertInstanceOf(CommandDisplaysOff.class, mapper.readValue("{\"_type\":\"display.off\"}", Command.class));
        assertEquals(List.of(), command.getDisplays());
    }

    @Test
    void chosenDisplaysRoundTrip() throws Exception {
        var json = mapper.writeValueAsString(new CommandDisplaysOff(List.of("3", "5")));
        var command = assertInstanceOf(CommandDisplaysOff.class, mapper.readValue(json, Command.class));
        assertEquals(List.of("3", "5"), command.getDisplays());
    }
}
