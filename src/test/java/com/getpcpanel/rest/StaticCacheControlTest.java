package com.getpcpanel.rest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StaticCacheControlTest {
    @Test
    void hashedBundleFilesStayImmutable() {
        assertTrue(StaticCacheControl.contentHashed("/main-FKWEF7SU.js"));
        assertTrue(StaticCacheControl.contentHashed("/chunk-444HCFI4.js"));
        assertTrue(StaticCacheControl.contentHashed("/styles-A1B2C3D4.css"));
        assertTrue(StaticCacheControl.contentHashed("/chunk-444HCFI4.js.map"));
        assertTrue(StaticCacheControl.contentHashed("/app-picker.component-FVWUOT2Q.css.map"));
    }

    @Test
    void entryPointAndUnhashedFilesRevalidate() {
        assertFalse(StaticCacheControl.contentHashed("/"));
        assertFalse(StaticCacheControl.contentHashed("/index.html"));
        assertFalse(StaticCacheControl.contentHashed("/favicon.ico"));
        assertFalse(StaticCacheControl.contentHashed("/control/ABC123/4"), "SPA deep link serves index.html content");
        assertFalse(StaticCacheControl.contentHashed("/assets/some-image.png"), "8-char names without a hash are not immutable");
    }

    @Test
    void nonStaticRoutesAreLeftAlone() {
        assertFalse(StaticCacheControl.isUiPath("/api/devices"));
        assertFalse(StaticCacheControl.isUiPath("/q/health"));
        assertFalse(StaticCacheControl.isUiPath("/ws/events"));
        assertTrue(StaticCacheControl.isUiPath("/"));
        assertTrue(StaticCacheControl.isUiPath("/index.html"));
        assertTrue(StaticCacheControl.isUiPath("/control/ABC123/4"));
    }
}
