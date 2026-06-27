package com.getpcpanel.rest;

import java.net.URI;
import java.util.Set;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.extern.log4j.Log4j2;

/**
 * Restricts the local HTTP server to the machine it runs on. The server already binds to
 * {@code 127.0.0.1} (so other hosts cannot reach it), but a loopback bind does not stop a website
 * the user visits in a browser from driving the API: WebSockets ignore the same-origin policy
 * (cross-site WebSocket hijacking) and DNS rebinding lets a foreign page reach {@code 127.0.0.1}
 * while keeping its own origin. This guard runs before every REST, static-file and WebSocket-upgrade
 * handler and rejects any request whose {@code Host} or {@code Origin} header names something other
 * than this machine.
 */
@Log4j2
@ApplicationScoped
public class LocalHttpGuard {
    /** Loopback host names accepted in the Host and Origin headers, on any port. */
    private static final Set<String> ALLOWED_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    @ConfigProperty(name = "pcpanel.http.local-only", defaultValue = "true")
    boolean enabled;

    void register(@Observes Router router) {
        if (!enabled) {
            log.warn("pcpanel.http.local-only is disabled; the loopback Host/Origin guard is OFF");
            return;
        }
        // Lowest order so it runs before the REST/static/WebSocket-upgrade handlers; on success it
        // hands off with next(), otherwise it ends the request with 403.
        router.route().order(Integer.MIN_VALUE).handler(this::guard);
        log.info("Restricting HTTP access to loopback connections only (Host/Origin guard active)");
    }

    private void guard(RoutingContext ctx) {
        var host = ctx.request().getHeader(HttpHeaders.HOST);
        var origin = ctx.request().getHeader(HttpHeaders.ORIGIN);
        if (hostAllowed(host) && originAllowed(origin)) {
            ctx.next();
            return;
        }
        log.debug("Rejected non-local request {} {} (Host={}, Origin={})", ctx.request().method(), ctx.request().path(), host, origin);
        ctx.response().setStatusCode(403).end("Forbidden: this server only accepts local connections");
    }

    /** A request's Host must name a loopback host — defeats DNS rebinding (attacker Host is their domain). */
    static boolean hostAllowed(String hostHeader) {
        return hostHeader != null && !hostHeader.isBlank() && ALLOWED_HOSTS.contains(stripPort(hostHeader.trim()));
    }

    /**
     * The browser sets Origin on cross-site requests and on WebSocket handshakes, and a page cannot
     * forge it; rejecting a non-loopback Origin defeats CSRF and cross-site WebSocket hijacking. An
     * absent Origin is allowed (non-browser clients omit it and the Host check is the backstop); a
     * literal {@code null} Origin (opaque: sandboxed iframe, {@code file://}, {@code data:}) is not
     * our own UI and is rejected.
     */
    static boolean originAllowed(String originHeader) {
        if (originHeader == null || originHeader.isBlank()) {
            return true;
        }
        if ("null".equalsIgnoreCase(originHeader.trim())) {
            return false;
        }
        var host = hostOf(originHeader.trim());
        return host != null && ALLOWED_HOSTS.contains(host);
    }

    private static String stripPort(String authority) {
        if (authority.startsWith("[")) { // IPv6 literal, e.g. [::1]:7654
            var end = authority.indexOf(']');
            return end >= 0 ? authority.substring(0, end + 1) : authority;
        }
        var colon = authority.indexOf(':');
        return colon >= 0 ? authority.substring(0, colon) : authority;
    }

    private static String hostOf(String origin) {
        try {
            return URI.create(origin).getHost();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
