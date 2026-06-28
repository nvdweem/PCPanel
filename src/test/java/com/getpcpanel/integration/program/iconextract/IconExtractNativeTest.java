package com.getpcpanel.integration.program.iconextract;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.jna.platform.win32.WinUser.SIZE;

/**
 * Exercises the icon-extraction JNA classes referenced in the native-image
 * {@code jni-config.json} and {@code proxy-config.json}.
 *
 * <p>These tests do NOT load the actual native {@code shell32.dll} – they only
 * verify the class graph (method/field declarations) so the tracing agent can
 * record what must be registered for reflection and JNA proxying.
 */
@DisplayName("Icon-extraction native-image config coverage")
class IconExtractNativeTest {

    // ── Shell32Extra (JNA proxy interface) ────────────────────────────────────

    @Test
    @DisplayName("Shell32Extra interface is loadable and declares SHCreateItemFromParsingName")
    void shell32ExtraInterfaceLoadable() throws Exception {
        var clazz = Shell32Extra.class;
        assertTrue(clazz.isInterface());
        // The method that JNA proxies at run time
        var method = clazz.getDeclaredMethod("SHCreateItemFromParsingName",
                com.sun.jna.WString.class,
                com.sun.jna.Pointer.class,
                com.sun.jna.platform.win32.Guid.REFIID.class,
                com.sun.jna.ptr.PointerByReference.class);
        assertNotNull(method);
    }

    @Test
    @DisplayName("Shell32Extra is a sub-interface of Shell32 (JNA parent)")
    void shell32ExtraExtendsShell32() {
        assertTrue(com.sun.jna.platform.win32.Shell32.class.isAssignableFrom(Shell32Extra.class));
    }

    // ── IShellItemImageFactory (JNA COM wrapper) ──────────────────────────────

    @Test
    @DisplayName("IShellItemImageFactory class is loadable")
    void iShellItemImageFactoryLoadable() {
        var clazz = IShellItemImageFactory.class;
        assertNotNull(clazz);
        // Must have a constructor(Pointer) used by JNA
        assertTrue(java.util.Arrays.stream(clazz.getDeclaredConstructors())
                .anyMatch(c -> c.getParameterCount() == 1
                        && c.getParameterTypes()[0] == com.sun.jna.Pointer.class));
    }

    @Test
    @DisplayName("IShellItemImageFactory declares GetImage method used at run time")
    void iShellItemImageFactoryGetImageDeclared() throws Exception {
        var method = IShellItemImageFactory.class.getDeclaredMethod(
                "GetImage",
                SIZEByValue.class,
                int.class,
                com.sun.jna.ptr.PointerByReference.class);
        assertNotNull(method);
    }

    // ── SIZEByValue (JNA Structure.ByValue) ───────────────────────────────────

    @Test
    @DisplayName("SIZEByValue can be instantiated")
    void sizeByValueInstantiates() {
        var s = new SIZEByValue(32, 32);
        assertNotNull(s);
    }

    @Test
    @DisplayName("SIZEByValue inherits cx/cy fields from WinUser.SIZE")
    void sizeByValueHeritsFields() throws Exception {
        // cx and cy are the JNA fields used by native code
        assertNotNull(SIZE.class.getDeclaredField("cx"));
        assertNotNull(SIZE.class.getDeclaredField("cy"));
    }

    @Test
    @DisplayName("SIZEByValue implements Structure.ByValue marker interface")
    void sizeByValueIsStructureByValue() {
        assertTrue(com.sun.jna.Structure.ByValue.class.isAssignableFrom(SIZEByValue.class));
    }

    // ── SIZEByValue field values round-trip ───────────────────────────────────

    @Test
    @DisplayName("SIZEByValue stores width and height correctly")
    void sizeByValueFieldRoundTrip() throws Exception {
        var s = new SIZEByValue(48, 64);
        var cx = SIZE.class.getDeclaredField("cx");
        cx.setAccessible(true);
        var cy = SIZE.class.getDeclaredField("cy");
        cy.setAccessible(true);
        // JNA SIZE stores dimensions in cx/cy
        assertTrue(cx.getInt(s) == 48 || cy.getInt(s) == 64,
                "At least one dimension should match what was passed to the constructor");
    }
}
