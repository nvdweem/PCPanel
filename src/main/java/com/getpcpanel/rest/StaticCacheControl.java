package com.getpcpanel.rest;

import java.util.regex.Pattern;

import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Keeps the UI fresh across app updates. Quarkus serves every static resource with
 * {@code Cache-Control: public, immutable, max-age=86400}. That is right for the content-hashed
 * bundle files (Angular {@code outputHashing=all} — a new build gets new names), but wrong for
 * {@code index.html} and the SPA-routed deep links that serve its content: {@code immutable} lets
 * the browser skip revalidation even on reload, so after an update it keeps running the previous
 * release's frontend against the new backend for up to a day (stale command catalog, dead
 * dropdowns). Everything without a content hash in its name is stamped {@code no-cache} instead,
 * which makes the browser revalidate each load ({@code 304 Not Modified} when unchanged).
 */
@ApplicationScoped
public class StaticCacheControl {
    /** Content-hashed bundle file, e.g. {@code main-FKWEF7SU.js} or {@code styles-ABCD1234.css.map}. */
    private static final Pattern HASHED = Pattern.compile("-[0-9A-Z]{8}\\.[a-z0-9.]+$");

    void register(@Observes Router router) {
        // After the loopback guard (MIN_VALUE), before the REST/static handlers.
        router.route().order(Integer.MIN_VALUE + 1).handler(StaticCacheControl::stampCacheControl);
    }

    private static void stampCacheControl(RoutingContext ctx) {
        var path = ctx.normalizedPath();
        if (isUiPath(path) && !contentHashed(path)) {
            ctx.addHeadersEndHandler(v -> ctx.response().putHeader(HttpHeaders.CACHE_CONTROL, "no-cache"));
        }
        ctx.next();
    }

    /** The non-static routes (REST, health, WebSocket upgrade) manage their own headers. */
    static boolean isUiPath(String path) {
        return !path.startsWith("/api") && !path.startsWith("/q/") && !path.startsWith("/ws");
    }

    static boolean contentHashed(String path) {
        return HASHED.matcher(path).find();
    }
}
