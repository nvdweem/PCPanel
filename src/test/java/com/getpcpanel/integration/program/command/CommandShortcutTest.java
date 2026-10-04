package com.getpcpanel.integration.program.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.AppLikeMapper;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.integration.program.IPlatformCommand;
import com.getpcpanel.integration.program.WindowFocuser;
import com.getpcpanel.integration.program.WindowFocuser.Result;

class CommandShortcutTest {
    private ObjectMapper mapper;
    private RecordingPlatform platform;
    private List<String> focusRequests;

    @BeforeEach
    void setUp() {
        mapper = AppLikeMapper.build();
        platform = new RecordingPlatform();
        focusRequests = new ArrayList<>();
    }

    @Test
    void anInstalledAppIsLabelledByItsName() {
        assertEquals("Spotify", new CommandShortcut("C:\\Start Menu\\Programs\\Spotify.LNK", null, false, false).buildLabel());
        assertEquals("firefox", new CommandShortcut("/usr/share/applications/firefox.desktop", null, false, false).buildLabel());
        assertEquals("Safari", new CommandShortcut("/Applications/Safari.app", null, false, false).buildLabel());
        assertEquals("WindowsTerminal", new CommandShortcut("shell:AppsFolder\\Microsoft.WindowsTerminal_8wekyb3d8bbwe!App", null, false, false).buildLabel());
        assertEquals("5319275A", new CommandShortcut("shell:AppsFolder\\5319275A_cv1g1gvanyjgm!App", null, false, false).buildLabel());
    }

    @Test
    void otherTargetsAreLabelledAsTyped() {
        assertEquals("https://example.com/a.lnk?x", new CommandShortcut("https://example.com/a.lnk?x", null, false, false).buildLabel());
        assertEquals("C:\\Tools\\tool.exe --flag", new CommandShortcut("C:\\Tools\\tool.exe --flag", null, false, false).buildLabel());
    }

    @Test
    void oldSaveWithOnlyAPathStillLoads() throws Exception {
        var json = "{\"_type\":\"program.shortcut\",\"shortcut\":\"C:\\\\x\\\\a.lnk\"}";

        var read = assertInstanceOf(CommandShortcut.class, mapper.readValue(json, Command.class));

        assertEquals("C:\\x\\a.lnk", read.getShortcut());
        assertFalse(read.isFocusIfRunning());
        assertFalse(read.isMinimizeIfFocused());
        assertNull(read.getFocusApp());
    }

    @Test
    void roundTripsTheFocusSettings() throws Exception {
        var json = "{\"_type\":\"program.shortcut\",\"shortcut\":\"C:\\\\Apps\\\\Spotify.exe\",\"focusApp\":\"Spotify.exe\",\"focusIfRunning\":true,\"minimizeIfFocused\":true}";

        var read = assertInstanceOf(CommandShortcut.class, mapper.readValue(json, Command.class));
        var again = assertInstanceOf(CommandShortcut.class, mapper.readValue(mapper.writeValueAsString(read), Command.class));

        assertEquals("Spotify.exe", again.getFocusApp());
        assertTrue(again.isFocusIfRunning());
        assertTrue(again.isMinimizeIfFocused());
    }

    @Test
    void defaultFocusAppIsTheExecutableName() {
        assertEquals("Spotify.exe", CommandShortcut.defaultFocusApp("C:\\Apps\\Spotify.exe"));
        assertEquals("code.EXE", CommandShortcut.defaultFocusApp("/opt/code.EXE"));
        assertNull(CommandShortcut.defaultFocusApp("https://x"));
        assertNull(CommandShortcut.defaultFocusApp("C:\\Users\\me\\Desktop\\Game.lnk"));
        assertNull(CommandShortcut.defaultFocusApp(""));
    }

    @Test
    void aRunningAppIsBroughtToTheFrontInsteadOfLaunched() {
        new CommandShortcut("C:\\Apps\\Spotify.exe", "Spotify.exe", true, false).execute(focuser(Result.FOCUSED), platform);

        assertEquals(List.of("Spotify.exe minimize=false"), focusRequests);
        assertEquals(List.of(), platform.opened);
    }

    @Test
    void aMinimisedAppIsNotLaunchedEither() {
        new CommandShortcut("C:\\Apps\\Spotify.exe", "Spotify.exe", true, true).execute(focuser(Result.MINIMIZED), platform);

        assertEquals(List.of("Spotify.exe minimize=true"), focusRequests);
        assertEquals(List.of(), platform.opened);
    }

    @Test
    void anAppThatIsNotRunningIsLaunched() {
        new CommandShortcut("C:\\Apps\\Spotify.exe", "Spotify.exe", true, false).execute(focuser(Result.NOT_RUNNING), platform);

        assertEquals(List.of("Spotify.exe minimize=false"), focusRequests);
        assertEquals(List.of("C:\\Apps\\Spotify.exe"), platform.opened);
    }

    @Test
    void withoutTheToggleTheFocuserIsNeverAsked() {
        new CommandShortcut("C:\\Apps\\Spotify.exe", "Spotify.exe", false, true).execute(focuser(Result.FOCUSED), platform);

        assertEquals(List.of(), focusRequests);
        assertEquals(List.of("C:\\Apps\\Spotify.exe"), platform.opened);
    }

    @Test
    void aBlankAppFallsBackToTheExecutableOfThePath() {
        new CommandShortcut("C:\\Apps\\Spotify.exe", " ", true, false).execute(focuser(Result.FOCUSED), platform);

        assertEquals(List.of("Spotify.exe minimize=false"), focusRequests);
    }

    private WindowFocuser focuser(Result result) {
        return (exe, minimizeIfFocused) -> {
            focusRequests.add(exe + " minimize=" + minimizeIfFocused);
            return result;
        };
    }

    private static final class RecordingPlatform extends IPlatformCommand {
        final List<String> opened = new ArrayList<>();

        @Override
        public void exec(String shortcut) {
            throw new AssertionError("a shortcut goes through openOrRun");
        }

        @Override
        public void open(String target) {
            throw new AssertionError("a shortcut goes through openOrRun");
        }

        @Override
        public void openOrRun(String target) {
            opened.add(target);
        }

        @Override
        public void kill(String process) {
        }

        @Override
        protected boolean isExecutable(File file) {
            return true;
        }
    }
}
