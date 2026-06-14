package com.getpcpanel.cpp.linux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

class LinuxProcessHelperTest {

    /** The app id lives under the [Application] section; a top-level name= (in [Instance]) must be ignored. */
    @Test
    void parsesApplicationNameFromFlatpakInfo() {
        var info = List.of(
                "[Instance]",
                "name=org.should.NotPickThis",
                "instance-id=12345",
                "",
                "[Application]",
                "name=org.mozilla.firefox",
                "runtime=runtime/org.freedesktop.Platform/x86_64/24.08");

        assertEquals("org.mozilla.firefox", LinuxProcessHelper.parseFlatpakAppId(info));
    }

    @Test
    void returnsNullWhenNoApplicationSection() {
        assertNull(LinuxProcessHelper.parseFlatpakAppId(List.of("[Instance]", "name=org.example")));
        assertNull(LinuxProcessHelper.parseFlatpakAppId(List.of()));
    }
}
