package com.getpcpanel.appwindow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LinksTest {
    private static final String APP = "http://localhost:7654/api/auth/bootstrap?nonce=abc";

    @Test
    void theApplicationsOwnPagesStayInTheWindow() {
        assertTrue(Links.sameOrigin(APP, "http://localhost:7654/settings"));
        assertTrue(Links.sameOrigin(APP, "HTTP://LOCALHOST:7654/"));
    }

    @Test
    void anotherHostPortOrSchemeIsAnotherSite() {
        assertFalse(Links.sameOrigin(APP, "http://localhost:4200/"));
        assertFalse(Links.sameOrigin(APP, "https://localhost:7654/"));
        assertFalse(Links.sameOrigin(APP, "http://127.0.0.1:7654/"));
        assertFalse(Links.sameOrigin(APP, "https://github.com/nvdweem/PCPanel"));
        assertFalse(Links.sameOrigin(APP, null));
        assertFalse(Links.sameOrigin(APP, "not a uri"));
    }

    @Test
    void websitesAndMailOpenInTheDesktopsHandler() {
        assertTrue(Links.opensExternally("https://github.com/nvdweem/PCPanel/issues/new"));
        assertTrue(Links.opensExternally("http://example.com/"));
        assertTrue(Links.opensExternally("mailto:someone@example.com"));
        assertFalse(Links.opensExternally("file:///etc/passwd"));
        assertFalse(Links.opensExternally("javascript:alert(1)"));
        assertFalse(Links.opensExternally(null));
    }
}
