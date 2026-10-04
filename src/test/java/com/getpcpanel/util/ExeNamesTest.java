package com.getpcpanel.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExeNamesTest {
    @Test
    void stemDropsDirectoryWhitespaceAndExe() {
        assertEquals("Discord", ExeNames.stem("C:\\Users\\me\\AppData\\Local\\Discord\\Discord.exe"));
        assertEquals("firefox", ExeNames.stem("/usr/lib/firefox/firefox"));
        assertEquals("Spotify", ExeNames.stem("  Spotify.EXE "));
        assertEquals("obs64", ExeNames.stem("obs64"));
        assertEquals("app.exe.bak", ExeNames.stem("app.exe.bak"));
    }

    @Test
    void stemOfNothingIsEmpty() {
        assertEquals("", ExeNames.stem(null));
        assertEquals("", ExeNames.stem("  "));
        assertEquals("", ExeNames.stem("C:\\Program Files\\"));
    }

    @Test
    void sameAppIgnoresPathCaseAndExe() {
        assertTrue(ExeNames.sameApp("Discord.exe", "c:/x/discord.EXE"));
        assertFalse(ExeNames.sameApp("Discord.exe", "Teams.exe"));
        assertFalse(ExeNames.sameApp("", ""));
        assertFalse(ExeNames.sameApp(null, "discord"));
    }
}
