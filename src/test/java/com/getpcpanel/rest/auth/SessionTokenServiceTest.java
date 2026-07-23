package com.getpcpanel.rest.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SessionTokenServiceTest {
    private final SessionTokenService service = new SessionTokenService();

    @Test
    void redeemingAValidNonceYieldsAValidSession() {
        var nonce = service.issueNonce();
        var token = service.redeemNonce(nonce).orElseThrow();
        assertTrue(service.isValidSession(token));
    }

    @Test
    void aNonceIsSingleUse() {
        var nonce = service.issueNonce();
        assertTrue(service.redeemNonce(nonce).isPresent());
        assertTrue(service.redeemNonce(nonce).isEmpty(), "a consumed nonce must not be redeemable again");
    }

    @Test
    void unknownOrBlankNoncesAreRejected() {
        assertTrue(service.redeemNonce("not-a-real-nonce").isEmpty());
        assertTrue(service.redeemNonce(null).isEmpty());
        assertTrue(service.redeemNonce("").isEmpty());
        assertTrue(service.redeemNonce("   ").isEmpty());
    }

    @Test
    void onlyIssuedTokensAreValidSessions() {
        var token = service.redeemNonce(service.issueNonce()).orElseThrow();
        assertTrue(service.isValidSession(token));
        assertFalse(service.isValidSession(token + "x"), "a tampered token is not valid");
        assertFalse(service.isValidSession("random"));
        assertFalse(service.isValidSession(null));
        assertFalse(service.isValidSession(""));
    }

    @Test
    void tokensAndNoncesAreUnique() {
        assertNotEquals(service.issueNonce(), service.issueNonce());
        var a = service.redeemNonce(service.issueNonce()).orElseThrow();
        var b = service.redeemNonce(service.issueNonce()).orElseThrow();
        assertNotEquals(a, b);
    }
}
