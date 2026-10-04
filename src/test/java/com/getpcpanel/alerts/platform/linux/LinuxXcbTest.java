package com.getpcpanel.alerts.platform.linux;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class LinuxXcbTest {
    @Test
    void windowClassIsTheSecondName() {
        assertEquals(Optional.of("discord"), LinuxXcb.windowClass("discord\0discord\0"));
        assertEquals(Optional.of("Slack"), LinuxXcb.windowClass("slack\0Slack\0"));
        assertEquals(Optional.of("xterm"), LinuxXcb.windowClass("xterm\0"));
        assertEquals(Optional.empty(), LinuxXcb.windowClass(""));
    }
}
