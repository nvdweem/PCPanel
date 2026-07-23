package com.getpcpanel.rest.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.log4j.Log4j2;

/**
 * Issues and validates the credential that authenticates the browser UI to the local API.
 *
 * <p>The loopback bind and {@link com.getpcpanel.rest.LocalHttpGuard} keep <em>remote</em> and
 * <em>cross-site-browser</em> callers out, but they do nothing to stop another program running on the
 * same machine from driving the unauthenticated API. This service adds a proof-of-caller: a per-session
 * secret that only the browser the user actually launched (via the tray) is handed.
 *
 * <p>Two secrets are used so the long-lived one never appears in a URL or a process command line:
 * <ul>
 *   <li>a single-use <b>nonce</b> — minted when the tray opens the UI and passed in the launch URL. It
 *       is consumed on first redemption and expires after {@link #NONCE_TTL}, so reading it out of the
 *       browser's command line after the fact is useless.</li>
 *   <li>a <b>session token</b> — minted in exchange for a valid nonce and returned to the browser only
 *       as an {@code HttpOnly} cookie, so it never transits a URL and page JS cannot read it.</li>
 * </ul>
 *
 * <p>All state is in-memory: a process restart empties it, invalidating every outstanding cookie, and
 * the user re-authenticates by opening the UI from the tray again. This is deliberately not persisted.
 */
@Log4j2
@ApplicationScoped
public class SessionTokenService {
    /** How long a freshly-minted bootstrap nonce stays redeemable. */
    private static final Duration NONCE_TTL = Duration.ofMinutes(2);
    /** 32 bytes = 256 bits of entropy per token; unguessable over the lifetime of the process. */
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    /** Single-use bootstrap nonces mapped to their expiry (epoch millis). */
    private final Map<String, Long> nonces = new ConcurrentHashMap<>();
    /** Valid session tokens for the lifetime of this process (cleared on restart). */
    private final CopyOnWriteArraySet<String> sessions = new CopyOnWriteArraySet<>();

    /** Mint a single-use nonce for a browser bootstrap (handed to the browser via the launch URL). */
    public String issueNonce() {
        purgeExpiredNonces();
        var nonce = randomToken();
        nonces.put(nonce, System.currentTimeMillis() + NONCE_TTL.toMillis());
        return nonce;
    }

    /**
     * Redeem a bootstrap nonce for a session token. The nonce is consumed (single-use); returns empty
     * if it is unknown, already used, or expired.
     */
    public Optional<String> redeemNonce(String nonce) {
        if (nonce == null || nonce.isBlank()) {
            return Optional.empty();
        }
        var expiry = nonces.remove(nonce);
        if (expiry == null || expiry < System.currentTimeMillis()) {
            return Optional.empty();
        }
        var token = randomToken();
        sessions.add(token);
        return Optional.of(token);
    }

    /** True if the given session token was issued by this process and is still valid. */
    public boolean isValidSession(String token) {
        return token != null && !token.isBlank() && sessions.contains(token);
    }

    private String randomToken() {
        var bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return encoder.encodeToString(bytes); // base64url without padding: URL- and cookie-safe
    }

    private void purgeExpiredNonces() {
        var now = System.currentTimeMillis();
        nonces.entrySet().removeIf(e -> e.getValue() < now);
    }
}
