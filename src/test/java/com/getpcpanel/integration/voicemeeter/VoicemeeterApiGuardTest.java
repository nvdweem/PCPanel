package com.getpcpanel.integration.voicemeeter;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link VoicemeeterAPI} fails cleanly when the native library never loaded, instead of
 * throwing a {@link NullPointerException} when the first API call dereferences a null instance.
 */
@DisplayName("VoicemeeterAPI null-instance guard")
class VoicemeeterApiGuardTest {

    @Test
    @DisplayName("login() throws VoicemeeterException (not NPE) when the library is not loaded")
    void loginWithoutInstanceThrowsClearException() {
        // No init() call → the JNA instance is null. saveService is unused by login().
        var api = new VoicemeeterAPI(null);

        var ex = assertThrows(VoicemeeterException.class, api::login);
        assertTrue(ex.getMessage().toLowerCase().contains("not loaded"),
                "expected a clear 'not loaded' message, got: " + ex.getMessage());
    }
}
