package com.getpcpanel.rest.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SessionAuthFilterTest {
    // ── Which paths require a session ──
    @Test
    void apiAndWebsocketPathsAreProtected() {
        assertTrue(SessionAuthFilter.isProtected("/api/settings"));
        assertTrue(SessionAuthFilter.isProtected("/api/system/quit"));
        assertTrue(SessionAuthFilter.isProtected("/ws/events"));
        // The session probe must be gated (unauth → 401), so a stale-cookie WebSocket rejection surfaces
        // the auth gate. Only /api/auth/bootstrap is additionally exempted (inside guard()).
        assertTrue(SessionAuthFilter.isProtected("/api/auth/status"));
    }

    @Test
    void theStaticShellIsNotProtected() {
        assertFalse(SessionAuthFilter.isProtected("/"));
        assertFalse(SessionAuthFilter.isProtected("/index.html"));
        assertFalse(SessionAuthFilter.isProtected("/main-ABC123.js"));
        assertFalse(SessionAuthFilter.isProtected("/assets/icon.png"));
    }

    /**
     * Regression guard for the CSRF-via-GET trap: gating is by path, not method, so a GET on the API is
     * required to carry the session exactly like a POST. If anyone adds a state-changing GET endpoint it
     * is still behind the session (and thus behind SameSite), not reachable cross-site.
     */
    @Test
    void protectionIsMethodAgnostic() {
        // The same path string is used regardless of the HTTP verb the request carried.
        assertTrue(SessionAuthFilter.isProtected("/api/profile/switch"));
    }

    // ── The gating decision guard() applies to the router-normalized path ──
    @Test
    void bootstrapIsExemptButOtherApiPathsRequireSession() {
        // The one gated path that mints the session cannot itself require one; everything else on the
        // API/WS surface does.
        assertFalse(SessionAuthFilter.requiresSession("/api/auth/bootstrap"));
        assertTrue(SessionAuthFilter.requiresSession("/api/auth/status"));
        assertTrue(SessionAuthFilter.requiresSession("/api/system/quit"));
        assertTrue(SessionAuthFilter.requiresSession("/ws/events"));
        assertFalse(SessionAuthFilter.requiresSession("/index.html"));
    }

    /**
     * The gate must run on the router-NORMALIZED path. These raw request-line forms normalize to a
     * protected /api path (which RESTEasy Reactive then dispatches to) but are not recognized by the
     * string prefix check, so guard() must feed it {@code ctx.normalizedPath()} rather than
     * {@code ctx.request().path()} — otherwise they reach the API with no session.
     */
    @Test
    void rawUnnormalizedApiFormsAreNotCaughtByTheStringCheck() {
        assertFalse(SessionAuthFilter.isProtected("//api/settings"));
        assertFalse(SessionAuthFilter.isProtected("/x/../api/settings"));
        // ...which is exactly why the gate keys off the normalized form, where they ARE protected:
        assertTrue(SessionAuthFilter.requiresSession("/api/settings"));
    }

    // ── Extracting the session token from the raw Cookie header (WebSocket handshake path) ──
    @Test
    void extractsTheSessionCookieAmongOthers() {
        var header = "theme=dark; " + SessionAuthFilter.COOKIE_NAME + "=abc123; other=1";
        assertEquals("abc123", SessionAuthFilter.sessionTokenFrom(header).orElseThrow());
    }

    @Test
    void extractsWhenItIsTheOnlyCookie() {
        assertEquals("tok", SessionAuthFilter.sessionTokenFrom(SessionAuthFilter.COOKIE_NAME + "=tok").orElseThrow());
    }

    @Test
    void missingOrBlankCookieHeaderYieldsEmpty() {
        assertTrue(SessionAuthFilter.sessionTokenFrom(null).isEmpty());
        assertTrue(SessionAuthFilter.sessionTokenFrom("").isEmpty());
        assertTrue(SessionAuthFilter.sessionTokenFrom("theme=dark; other=1").isEmpty());
    }
}
