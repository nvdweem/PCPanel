package com.getpcpanel.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.AppLikeMapper;
import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.command.CommandUnknown;
import com.getpcpanel.integration.program.command.CommandRun;

/** Actions written by a newer build, holding a command this build does not know, load and keep that command. */
class UnknownCommandSaveTest {
    private static final String NEWER_COMMAND = """
            {"_type":"future.command","level":3,"nested":{"commands":{"commands":[{"_type":"program.run","command":"x"}],"type":"allAtOnce"}}}""";
    private static final String COMMANDS = """
            {"commands":[%s,{"_type":"program.run","command":"notepad.exe"}],"type":"allAtOnce"}""".formatted(NEWER_COMMAND);

    private final ObjectMapper mapper = AppLikeMapper.build();

    @Test
    void loadsAndWritesTheUnknownCommandBackUnchanged() throws Exception {
        var read = mapper.readValue(COMMANDS, Commands.class);
        var commands = read.getCommands();
        assertEquals(2, commands.size(), "the known command next to it is kept too");
        assertEquals("future.command", assertInstanceOf(CommandUnknown.class, commands.getFirst()).type());
        assertInstanceOf(CommandRun.class, commands.get(1));

        var rewritten = mapper.readTree(mapper.writeValueAsString(read));
        assertEquals(mapper.readTree(NEWER_COMMAND), rewritten.at("/commands/0"));
        assertEquals("program.run", rewritten.at("/commands/1/_type").asText());
    }

    @Test
    void survivesASecondRoundTrip() throws Exception {
        var once = mapper.writeValueAsString(mapper.readValue(COMMANDS, Commands.class));
        var twice = mapper.writeValueAsString(mapper.readValue(once, Commands.class));
        assertEquals(mapper.readTree(once), mapper.readTree(twice));
    }
}
