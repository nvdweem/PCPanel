package com.getpcpanel.integration.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.program.IPlatformCommand.WindowsPlatformCommand;

class WindowsPlatformCommandTest {

    @Test
    void realExecutablesLaunchWithoutAShell() {
        assertTrue(WindowsPlatformCommand.canLaunchDirectly(new File("notepad.exe")));
        assertTrue(WindowsPlatformCommand.canLaunchDirectly(new File("C:\\tools\\FOO.EXE")), "extension check is case-insensitive");
        assertTrue(WindowsPlatformCommand.canLaunchDirectly(new File("legacy.com")));
    }

    @Test
    void associationBackedTargetsKeepTheShell() {
        // These can't be CreateProcess'd directly, so they must fall through to the cmd.exe path.
        assertFalse(WindowsPlatformCommand.canLaunchDirectly(new File("game.lnk")));
        assertFalse(WindowsPlatformCommand.canLaunchDirectly(new File("run.bat")));
        assertFalse(WindowsPlatformCommand.canLaunchDirectly(new File("run.cmd")));
        assertFalse(WindowsPlatformCommand.canLaunchDirectly(new File("setup.msi")));
        assertFalse(WindowsPlatformCommand.canLaunchDirectly(new File("script.ps1")));
        assertFalse(WindowsPlatformCommand.canLaunchDirectly(new File("no-extension")));
    }

    @Test
    void executableArgvKeepsMetacharactersInOneArgument() {
        // The whole path is a single argument: spaces and shell metacharacters are never split or
        // interpreted, which is the robustness the cmd.exe /c "..." string could not guarantee.
        var exe = new File("C:\\Program Files\\Rock & Roll\\my ^app%.exe");
        assertEquals(List.of(exe.getAbsolutePath()), WindowsPlatformCommand.executableArgv(exe));
    }

    @Test
    void directoryArgvOpensExplorerWithTheRawPath() {
        var dir = new File("C:\\some & dir");
        assertEquals(List.of("explorer.exe", dir.getAbsolutePath()), WindowsPlatformCommand.directoryArgv(dir));
    }
}
