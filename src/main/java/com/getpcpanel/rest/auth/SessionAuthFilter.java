package com.getpcpanel.rest.auth;

import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * Requires a valid session cookie on every API and WebSocket request, so an unauthenticated local
 * process cannot drive the control plane merely by reaching the loopback port. This is the
 * authentication layer that sits behind {@link com.getpcpanel.rest.LocalHttpGuard} (the network-origin
 * layer): the guard decides <em>where</em> a request may come from, this filter decides <em>whether the
 * caller holds the session</em> the tray handed to the real browser.
 *
 * <p>Only {@code /api/**} and {@code /ws/**} are gated. The static UI shell (index.html, JS bundles)
 * is served without a session — it holds no secret, and a caller cannot do anything with it until it
 * has the cookie. The one exception among the gated paths is {@link #BOOTSTRAP_PATH}, which mints the
 * session and therefore cannot itself require one (it is authenticated by the single-use nonce instead).
 *
 * <p>Disable with {@code pcpanel.http.require-session=false} (off in the {@code dev} profile so
 * {@code quarkus:dev} and a standalone Angular dev server work without the tray handshake).
 */
@Log4j2
@ApplicationScoped
public class SessionAuthFilter {
    /** Name of the session cookie set at bootstrap and required on every API/WS request. */
    public static final String COOKIE_NAME = "PCPANEL_SESSION";
    /** The one gated endpoint that mints the session, so it cannot itself require a session. */
    private static final String BOOTSTRAP_PATH = "/api/auth/bootstrap";

    @ConfigProperty(name = "pcpanel.http.require-session", defaultValue = "true")
    boolean enabled;

    @Inject SessionTokenService tokens;

    void register(@Observes Router router) {
        if (!enabled) {
            log.warn("pcpanel.http.require-session is disabled; the API session-auth layer is OFF");
            return;
        }
        // One order past LocalHttpGuard (MIN_VALUE) so the loopback check runs first; a single route
        // gates the API + WS upgrade for the whole server, leaving no per-resource holes.
        router.route().order(Integer.MIN_VALUE + 1).handler(this::guard);
        log.info("Requiring a session cookie on /api and /ws requests (session-auth active)");
    }

    private void guard(RoutingContext ctx) {
        // Gate on the router-normalized path, NOT ctx.request().path(). The raw request-line path keeps
        // `.`/`..` segments and repeated slashes, so a path like `//api/system/quit` or `/x/../api/settings`
        // fails the startsWith("/api/") check below yet still normalizes to a protected /api path that
        // RESTEasy Reactive matches and dispatches to — reaching the endpoint with no session. normalizedPath()
        // is exactly the value the router matches on, so the gate and the routing agree on what the path is.
        // requiresSession() additionally strips matrix parameters, the other place the two can disagree.
        var path = ctx.normalizedPath();
        if (!requiresSession(path)) {
            ctx.next();
            return;
        }
        var cookie = ctx.request().getCookie(COOKIE_NAME);
        if (cookie != null && tokens.isValidSession(cookie.getValue())) {
            ctx.next();
            return;
        }
        log.debug("Rejecting unauthenticated request {} {} (no valid session cookie)", ctx.request().method(), path);
        // no-store so a soft browser reload always re-checks the session against the server rather than
        // serving a stale cached response (which would mask the auth state — see the auth probe).
        ctx.response().setStatusCode(401).putHeader("Cache-Control", "no-store").end("Unauthorized: open PCPanel from the tray icon");
    }

    /**
     * The control surface that must be authenticated: the JSON API and the event WebSocket. This is
     * intentionally method-agnostic — a GET on {@code /api} is gated exactly like a POST — which is what
     * keeps a future state-changing GET from being reachable cross-site without the session (the
     * CSRF-via-GET trap {@link com.getpcpanel.rest.LocalHttpGuard} leaves open by allowing an absent
     * Origin).
     */
    static boolean isProtected(String path) {
        return path.startsWith("/api/") || path.startsWith("/ws/");
    }

    /**
     * Whether a request to this (router-normalized) path must carry a session: the gated API/WS surface,
     * minus {@link #BOOTSTRAP_PATH}, which mints the session and so cannot itself require one. Callers must
     * pass the normalized path (see {@link #guard}) so this decision matches what the router dispatches on.
     *
     * <p>Normalization alone is not enough: the path is gated if <em>either</em> its normalized form or its
     * {@linkplain #withoutMatrixParams matrix-parameter-stripped} form names the API/WS surface, so a form
     * that only one of the two recognises still fails closed. The exemption keys off the stripped form —
     * the endpoint a request actually reaches — so it cannot be widened by decorating the path.
     */
    static boolean requiresSession(String path) {
        var routed = withoutMatrixParams(path);
        return (isProtected(path) || isProtected(routed)) && !BOOTSTRAP_PATH.equals(routed);
    }

    /**
     * The normalized path with each segment's matrix parameters (RFC 3986 {@code ;name=value}) removed —
     * the form RESTEasy Reactive matches resources on. The router drops those parameters before matching,
     * so the gate has to see the same path: {@code /api;x=1/system/quit} does not start with {@code /api/}
     * and would otherwise pass the prefix check ungated while still being dispatched to
     * {@code /api/system/quit}. Only a literal {@code ;} matters — a percent-encoded {@code %3B} is not a
     * parameter delimiter and is not matched as one by the router either.
     */
    static String withoutMatrixParams(String path) {
        if (path.indexOf(';') < 0) {
            return path;
        }
        var segments = path.split("/", -1);
        for (var i = 0; i < segments.length; i++) {
            var semicolon = segments[i].indexOf(';');
            if (semicolon >= 0) {
                segments[i] = segments[i].substring(0, semicolon);
            }
        }
        return String.join("/", segments);
    }

    /**
     * Extracts the session token from a raw {@code Cookie} request header, for callers that do not run
     * through the Vert.x router (the WebSocket handshake reads the header directly).
     */
    public static Optional<String> sessionTokenFrom(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isBlank()) {
            return Optional.empty();
        }
        for (var part : cookieHeader.split(";")) {
            var kv = part.trim();
            var eq = kv.indexOf('=');
            if (eq > 0 && kv.substring(0, eq).trim().equals(COOKIE_NAME)) {
                return Optional.of(kv.substring(eq + 1).trim());
            }
        }
        return Optional.empty();
    }
}
