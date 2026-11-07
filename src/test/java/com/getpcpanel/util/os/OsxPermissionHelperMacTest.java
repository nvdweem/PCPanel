package com.getpcpanel.util.os;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Calls {@code AXIsProcessTrustedWithOptions} against the real ApplicationServices: the options dictionary, AX's own
 * prompt key and the CoreFoundation booleans must all resolve. Enable with {@code -Dpcpanel.mactest=true}; the
 * {@code macOS native tests} workflow runs it on GitHub's macOS images.
 */
@EnabledIfSystemProperty(named = "pcpanel.mactest", matches = "true")
class OsxPermissionHelperMacTest {
    @Test
    void accessibilityCheckWithOptionsAgreesWithThePlainCheck() {
        var plain = OsxPermissionHelper.isAccessibilityGranted();
        var withoutPrompt = OsxPermissionHelper.accessibilityTrusted(false);
        var withPrompt = OsxPermissionHelper.accessibilityTrusted(true);
        System.out.printf("Accessibility: plain=%s, withOptions=%s, withPrompt=%s%n", plain, withoutPrompt, withPrompt);
        assertEquals(plain, withoutPrompt);
        assertEquals(plain, withPrompt);
    }
}
