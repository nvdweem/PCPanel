package com.getpcpanel.integration.display.platform.linux;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class LinuxDisplayPowerTest {
    @Test
    void x11TriesKdeThenXset() {
        assertEquals(List.of("kscreen-doctor", "xset"), tools(Map.of("XDG_SESSION_TYPE", "x11", "DISPLAY", ":0")));
    }

    @Test
    void waylandSkipsXsetWhichOnlyReachesXwayland() {
        assertEquals(List.of("kscreen-doctor"), tools(Map.of("XDG_SESSION_TYPE", "wayland")));
        assertEquals(List.of("kscreen-doctor"), tools(Map.of("WAYLAND_DISPLAY", "wayland-0", "DISPLAY", ":0")));
    }

    private static List<String> tools(Map<String, String> env) {
        return LinuxDisplayPower.commands(env).stream().map(c -> c[0]).toList();
    }
}
