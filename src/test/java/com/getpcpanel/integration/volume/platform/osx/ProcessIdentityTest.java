package com.getpcpanel.integration.volume.platform.osx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

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

    @Test
    void anExpiredIdentityDoesNotWatchTheProcessesNewLifetime() {
        var current = ProcessHandle.current();
        var expired = new ProcessIdentity((int) current.pid(), current.info().startInstant().orElseThrow().minusSeconds(1));
        var released = new AtomicBoolean();
        ProcessIdentity.onExit(expired, () -> released.set(true));
        assertTrue(released.get());
        assertTrue(current.isAlive());
    }

    @Test
    @EnabledOnOs({ OS.MAC, OS.LINUX })
    void exitNotificationRunsWithoutAnotherIdentityLookup() throws Exception {
        // cat waits for EOF on our pipe. No GUI application or audio device is touched.
        var process = new ProcessBuilder("/bin/cat").start();
        try {
            var identity = ProcessIdentity.find((int) process.pid());
            assertNotNull(identity);
            var released = new CompletableFuture<Void>();
            ProcessIdentity.onExit(identity, () -> released.complete(null));
            assertFalse(released.isDone());
            process.getOutputStream().close();
            released.get(5, TimeUnit.SECONDS);
            assertFalse(process.isAlive());
        } finally {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
    }
}
