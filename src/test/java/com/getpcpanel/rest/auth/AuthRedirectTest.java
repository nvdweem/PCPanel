package com.getpcpanel.rest.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.getpcpanel.util.app.ShowMainEvent;

@DisplayName("Bootstrap redirect (in-app paths only)")
class AuthRedirectTest {
    @Test
    @DisplayName("an in-app path is honoured, so a tray entry can open the screen it names")
    void allowsInAppPaths() {
        // The literal the tray actually sends, so retargeting it cannot silently start falling back to "/".
        assertEquals(ShowMainEvent.REPORT_PATH, AuthResource.safeRedirect(ShowMainEvent.REPORT_PATH));
        assertEquals("/", AuthResource.safeRedirect("/"));
        assertEquals("/device/ABC-123", AuthResource.safeRedirect("/device/ABC-123"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "//evil.example.com",
            "http://evil.example.com",
            "https://evil.example.com/path",
            "/\\evil.example.com",
            "javascript:alert(1)",
            "settings",
    })
    @DisplayName("anything that could leave the app falls back to the start page")
    void rejectsEverythingElse(String redirect) {
        assertEquals("/", AuthResource.safeRedirect(redirect));
    }

    @Test
    @DisplayName("no redirect at all lands on the start page")
    void defaultsToRoot() {
        assertEquals("/", AuthResource.safeRedirect(null));
    }
}
