package com.getpcpanel.util.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ReconnectBackoff")
class ReconnectBackoffTest {

    @Test
    @DisplayName("is ready immediately before any failure")
    void readyInitially() {
        var b = new ReconnectBackoff(1_000, 30_000);
        assertTrue(b.ready(0));
        assertEquals(0, b.failures());
    }

    @Test
    @DisplayName("delay doubles on each consecutive failure")
    void exponentialGrowth() {
        var b = new ReconnectBackoff(1_000, 60_000);

        b.onFailure(0);                 // next attempt at 0 + 1000
        assertFalse(b.ready(999));
        assertTrue(b.ready(1_000));

        b.onFailure(1_000);             // delay 2000 -> next at 3000
        assertFalse(b.ready(2_999));
        assertTrue(b.ready(3_000));

        b.onFailure(3_000);             // delay 4000 -> next at 7000
        assertFalse(b.ready(6_999));
        assertTrue(b.ready(7_000));

        assertEquals(3, b.failures());
    }

    @Test
    @DisplayName("delay is capped at maxDelayMs")
    void delayCapped() {
        var b = new ReconnectBackoff(1_000, 5_000);
        for (var i = 0; i < 20; i++) {
            b.onFailure(0); // huge shift, but capped
        }
        // next attempt is at most now + max, never further
        assertFalse(b.ready(4_999));
        assertTrue(b.ready(5_000));
    }

    @Test
    @DisplayName("onSuccess resets the gate to immediately ready")
    void successResets() {
        var b = new ReconnectBackoff(1_000, 30_000);
        b.onFailure(0);
        b.onFailure(1_000);
        assertFalse(b.ready(1_500));

        b.onSuccess();
        assertTrue(b.ready(1_500));
        assertEquals(0, b.failures());
    }

    @Test
    @DisplayName("rejects invalid bounds")
    void validatesArgs() {
        assertThrows(IllegalArgumentException.class, () -> new ReconnectBackoff(0, 10));
        assertThrows(IllegalArgumentException.class, () -> new ReconnectBackoff(100, 10));
    }
}
