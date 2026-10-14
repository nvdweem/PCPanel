package com.getpcpanel.integration.volume.platform.osx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ProcessIdentityTest {
    @Test
    void identityUsesTheLiveProcessesStartTime() {
        var process = ProcessHandle.current();
        var identity = ProcessIdentity.find((int) process.pid());
        assertNotNull(identity);
        assertEquals(process.pid(), identity.pid());
        assertEquals(process.info().startInstant().orElseThrow(), identity.startedAt());
        assertEquals(identity, ProcessIdentity.find(identity.pid()));
    }

    @Test
    void invalidPidsHaveNoIdentity() {
        assertNull(ProcessIdentity.find(-1));
        assertNull(ProcessIdentity.find(0));
    }
}
