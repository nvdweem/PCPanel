package com.getpcpanel.cpp.linux;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import com.getpcpanel.commands.command.CommandKeystroke;

/**
 * Generation-only test: drives the real keystroke feature ({@link CommandKeystroke#execute()} ->
 * {@code KeyMacro} -> {@link LinuxKeyboard}) so the GraalVM tracing agent records the X11/XTest JNA
 * dynamic proxies that {@code Native.load} builds. Run under the agent it captures the metadata
 * organically from the application path — nothing about the proxies is hand-coded here.
 *
 * <p>It is disabled by default because reaching {@code XTestFakeKeyEvent} synthesises a real key
 * event on the live X display. It runs only when explicitly generating native config — the
 * {@code native-config-gen} Maven profile sets {@code pcpanel.generate-native-config=true} (see
 * README "Generate reachability metadata"). The key is {@code F24}: a valid X11 keysym that
 * virtually nothing binds, so the synthesised event disturbs nobody.
 */
@EnabledOnOs(OS.LINUX)
@EnabledIfSystemProperty(named = "pcpanel.generate-native-config", matches = "true")
@DisplayName("Linux keystroke native-config generation")
class LinuxKeyboardNativeConfigTest {

    @Test
    @DisplayName("CommandKeystroke(F24).execute() loads libX11/libXtst through JNA")
    void commandKeystrokeExercisesNativeKeyboard() {
        assertDoesNotThrow(() -> new CommandKeystroke("F24").execute());
    }
}
