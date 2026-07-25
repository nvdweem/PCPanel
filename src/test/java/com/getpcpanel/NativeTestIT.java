package com.getpcpanel;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;

import java.util.List;

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

    /**
     * Serialises the {@code PlatformInfo} record, which is always populated regardless of hardware. A
     * record reached only through a REST return type is exactly the shape that throws
     * {@code MissingReflectionRegistrationError} in the image while working in JVM/dev, so asserting a
     * real field is non-blank proves Jackson could actually read it reflectively.
     */
    @Test
    public void platformInfoSerialises() {
        given()
                .when().get("/api/platform")
                .then().statusCode(200)
                .body("os", not(emptyOrNullString()));
    }

    /**
     * The DTO-list endpoints. These are where the native reflection gap bites: a {@code List<SomeDto>}
     * also needs {@code SomeDto[]} registered, because Jackson instantiates that array reflectively per
     * collection.
     *
     * <p>Only the status is asserted, deliberately. Every one of these lists is sourced from
     * {@link com.getpcpanel.integration.volume.platform.ISndCtrl}, which degrades to a no-op on a
     * headless CI runner, so none of them can be relied on to be non-empty here — and an <em>empty</em>
     * list never instantiates the array, so it cannot prove the array form is registered. What this does
     * catch is every other native-only failure on these paths (an unregistered element record, a missing
     * Jackson {@code StdSerializer} for a field type, a platform bean that fails to resolve in the
     * image), and it catches the array gap too on any runner that does have audio state. Closing the
     * array case for certain needs a fixture that forces a non-empty list; see CLAUDE.md.
     */
    @Test
    public void dtoListEndpointsSerialise() {
        for (var path : List.of("/api/processes",
                "/api/audio/devices", "/api/audio/devices/output", "/api/audio/devices/input",
                "/api/audio/sessions", "/api/audio/applications")) {
            given().when().get(path).then().statusCode(200);
        }
    }
}
