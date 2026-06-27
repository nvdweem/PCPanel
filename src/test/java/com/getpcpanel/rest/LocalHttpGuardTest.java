package com.getpcpanel.rest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LocalHttpGuardTest {
    // ── Host header: must name a loopback host (defeats DNS rebinding) ──
    @Test
    void loopbackHostsAreAllowed() {
        assertTrue(LocalHttpGuard.hostAllowed("127.0.0.1:7654"));
        assertTrue(LocalHttpGuard.hostAllowed("localhost:7654"));
        assertTrue(LocalHttpGuard.hostAllowed("127.0.0.1"));
        assertTrue(LocalHttpGuard.hostAllowed("[::1]:7654"));
        assertTrue(LocalHttpGuard.hostAllowed("[::1]"));
    }

    @Test
    void foreignOrMissingHostIsRejected() {
        assertFalse(LocalHttpGuard.hostAllowed("evil.com"), "DNS-rebinding Host must be rejected");
        assertFalse(LocalHttpGuard.hostAllowed("evil.com:7654"));
        assertFalse(LocalHttpGuard.hostAllowed("127.0.0.1.evil.com"));
        assertFalse(LocalHttpGuard.hostAllowed(null));
        assertFalse(LocalHttpGuard.hostAllowed(""));
    }

    // ── Origin header: present-and-foreign is rejected; absent is allowed (Host is the backstop) ──
    @Test
    void loopbackOriginsAreAllowedOnAnyPort() {
        assertTrue(LocalHttpGuard.originAllowed("http://127.0.0.1:7654"));
        assertTrue(LocalHttpGuard.originAllowed("http://localhost:7654"));
        assertTrue(LocalHttpGuard.originAllowed("http://localhost:4200"), "dev Quinoa origin must pass");
        assertTrue(LocalHttpGuard.originAllowed("http://[::1]:7654"));
    }

    @Test
    void absentOriginIsAllowed() {
        assertTrue(LocalHttpGuard.originAllowed(null), "non-browser clients omit Origin");
        assertTrue(LocalHttpGuard.originAllowed(""));
    }

    @Test
    void foreignOrOpaqueOriginIsRejected() {
        assertFalse(LocalHttpGuard.originAllowed("http://evil.com"), "cross-site fetch/WS must be rejected");
        assertFalse(LocalHttpGuard.originAllowed("https://evil.com:7654"));
        assertFalse(LocalHttpGuard.originAllowed("http://127.0.0.1.evil.com"));
        assertFalse(LocalHttpGuard.originAllowed("null"), "opaque origin (sandboxed iframe/file/data) is not our UI");
        assertFalse(LocalHttpGuard.originAllowed("not a url"));
    }
}
