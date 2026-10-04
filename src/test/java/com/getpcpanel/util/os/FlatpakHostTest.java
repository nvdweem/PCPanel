package com.getpcpanel.util.os;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class FlatpakHostTest {
    @Test
    void wrapRunsTheCommandOnTheHost() {
        assertArrayEquals(new String[] { "flatpak-spawn", "--host", "xdg-open", "https://example.com" }, FlatpakHost.wrap("xdg-open", "https://example.com"));
    }

    @Test
    void outsideTheSandboxTheCommandIsUnchanged() {
        var cmd = new String[] { "loginctl", "lock-session" };
        assertArrayEquals(FlatpakHost.inSandbox() ? FlatpakHost.wrap(cmd) : cmd, FlatpakHost.command(cmd));
    }
}
