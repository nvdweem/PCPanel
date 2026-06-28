package com.getpcpanel.integration.keyboard.platform.linux;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Generation-only test: drives the real keystroke feature so the GraalVM tracing agent records the
 * X11 and XTest JNA dynamic proxies that {@code Native.load} builds, letting
 * {@code reachability-metadata.json} be regenerated from a run instead of hand-edited.
 *
 * <p>Disabled by default; runs only under the {@code native-config-gen} Maven profile (which sets
 * {@code pcpanel.generate-native-config=true}). It presses {@code PAUSE}: a key that has a real
 * keycode on essentially every keymap — so the feature reaches {@code XTestFakeKeyEvent} and loads
 * both libX11 (opening the display) and libXtst (synthesising the event), capturing both proxies
 * through the genuine code path — yet does nothing in virtually any application, so the synthesised
 * keypress is harmless. (F24 would be quieter still but has no keycode, so it never loads libXtst.)
 *
 * <p>It drives {@link LinuxKeyboard} directly rather than through {@code CommandKeystroke}: the
 * command resolves the {@code Keyboard} bean via {@code CdiHelper}, and this plain surefire run has
 * no CDI container, so {@code CommandKeystroke.execute()} would throw {@code Unable to locate
 * CDIProvider} before reaching any native call. Going straight to the backend exercises the exact
 * same {@code executeKeyStroke} → X11/XTest path the agent needs to observe.
 *
 * <p>That the proxies are actually <em>registered</em> is guaranteed by
 * {@link com.getpcpanel.integration.volume.platform.ProxyRegistrationCoverageTest}; this test only makes the agent observe
 * them during generation.
 */
@EnabledOnOs(OS.LINUX)
@EnabledIfSystemProperty(named = "pcpanel.generate-native-config", matches = "true")
@DisplayName("Linux keystroke native-config generation")
class LinuxKeyboardNativeConfigTest {

    @Test
    @DisplayName("pressing PAUSE loads libX11 + libXtst so the agent records both JNA proxies")
    void loadsX11AndXTestForAgent() {
        assertDoesNotThrow(() -> new LinuxKeyboard().executeKeyStroke("PAUSE"));
    }
}
