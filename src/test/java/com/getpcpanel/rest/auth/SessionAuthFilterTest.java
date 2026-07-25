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

    /**
     * Matrix parameters (RFC 3986 {@code ;name=value}) are the second place the gate and the router can
     * disagree about a path: RESTEasy Reactive strips them before matching a resource, so {@code /api;x=1/settings}
     * is dispatched to {@code /api/settings} while the raw prefix check sees a path that does not start with
     * {@code /api/}. Verified against a running server before the fix: every form below returned the real
     * endpoint (200/202/204) with no session cookie, including {@code POST /api;x/system/quit}.
     */
    @Test
    void matrixParameterFormsStillRequireASession() {
        assertTrue(SessionAuthFilter.requiresSession("/api;x=1/settings"));
        assertTrue(SessionAuthFilter.requiresSession("/api;/settings"));
        assertTrue(SessionAuthFilter.requiresSession("/api;a=b;c=d/system/quit"));
        assertTrue(SessionAuthFilter.requiresSession("/ws;a=b/events"));
        // ...and combined with the normalization forms the guard already collapses.
        assertTrue(SessionAuthFilter.requiresSession("/api;a=b/./settings"));
    }

    /** A decorated bootstrap path is still the bootstrap endpoint, so it stays exempt rather than 401-ing. */
    @Test
    void aDecoratedBootstrapPathIsStillExempt() {
        assertFalse(SessionAuthFilter.requiresSession("/api;x=1/auth/bootstrap"));
        assertFalse(SessionAuthFilter.requiresSession("/api/auth/bootstrap;x=1"));
    }

    /** Stripping must not turn an unprotected static path into a protected one, or the UI shell 401s. */
    @Test
    void staticPathsAreUnaffectedByMatrixStripping() {
        assertFalse(SessionAuthFilter.requiresSession("/index.html"));
        assertFalse(SessionAuthFilter.requiresSession("/main-ABC123.js"));
        assertFalse(SessionAuthFilter.requiresSession("/assets/icon.png;v=2"));
    }

    @Test
    void withoutMatrixParamsStripsEverySegment() {
        assertEquals("/api/settings", SessionAuthFilter.withoutMatrixParams("/api;x=1/settings"));
        assertEquals("/api/settings", SessionAuthFilter.withoutMatrixParams("/api;a=b;c=d/settings;e=f"));
        // Untouched when there is nothing to strip, and a percent-encoded ';' is not a delimiter.
        assertEquals("/api/settings", SessionAuthFilter.withoutMatrixParams("/api/settings"));
        assertEquals("/api%3Bx/settings", SessionAuthFilter.withoutMatrixParams("/api%3Bx/settings"));
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
