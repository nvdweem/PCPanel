package com.getpcpanel.rest.auth;

import java.net.URI;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import lombok.extern.log4j.Log4j2;

/**
 * The bootstrap handshake: turns the single-use nonce (handed to the browser in the tray-launched URL)
 * into an {@code HttpOnly} session cookie, then redirects to the app so the nonce leaves the visible URL.
 * This is the only gated endpoint exempt from {@link SessionAuthFilter} — it authenticates by the nonce,
 * because it is what issues the session in the first place.
 */
@Log4j2
@Path("/api/auth")
@ApplicationScoped
public class AuthResource {
    @Inject SessionTokenService tokens;

    @GET
    @Path("/bootstrap")
    public Response bootstrap(@QueryParam("nonce") String nonce) {
        var token = tokens.redeemNonce(nonce).orElse(null);
        if (token == null) {
            log.debug("Bootstrap rejected: nonce missing, expired, or already used");
            return Response.status(Response.Status.FORBIDDEN)
                           .entity("Invalid or expired link. Open PCPanel from the tray icon.")
                           .build();
        }
        // HttpOnly: page JS (and any XSS) cannot read the token. SameSite=Strict: the browser never
        // attaches it to a cross-site request, which is what keeps the auto-sent cookie from
        // reintroducing CSRF/cross-site-WebSocket. Path=/ covers /api and /ws. No Secure attribute:
        // the app is served over plain http on loopback. The redirect drops the nonce from the URL.
        var cookie = SessionAuthFilter.COOKIE_NAME + "=" + token + "; Path=/; HttpOnly; SameSite=Strict";
        return Response.seeOther(URI.create("/"))
                       .header("Set-Cookie", cookie)
                       .build();
    }
}
