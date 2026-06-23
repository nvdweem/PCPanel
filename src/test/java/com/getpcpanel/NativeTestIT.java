package com.getpcpanel;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Runs against the built native binary (failsafe, {@code mvn verify -Pnative}). These tests catch
 * native-image-only regressions that JVM-mode unit tests cannot see — most importantly that the
 * AWT/Java2D subsystem actually loads in the image. A broken GraalVM shim leaves {@code awt.dll}
 * unloadable, which silently kills the overlay and the font picker; that is exactly the kind of
 * Windows-only breakage that must fail the build rather than ship.
 */
@QuarkusIntegrationTest
public class NativeTestIT {
    @Test
    public void overlayEndpointResponds() {
        given().when().get("/api/overlay").then().statusCode(200);
    }

    /**
     * Guards the AWT-in-native-image contract. On Windows the overlay renders with headless Java2D, so
     * {@code GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()} must
     * succeed and return at least one family. When the native image ships an empty {@code java.dll}/
     * {@code jvm.dll} shim, {@code awt.dll} fails to load, {@code OverlayResource.fonts()} catches the
     * {@code UnsatisfiedLinkError} and returns an empty list — which this assertion turns into a build
     * failure instead of a broken installer.
     */
    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void fontEnumerationProvesAwtLoads() {
        given()
                .when().get("/api/overlay/fonts")
                .then().statusCode(200)
                .body("size()", greaterThan(0));
    }
}
