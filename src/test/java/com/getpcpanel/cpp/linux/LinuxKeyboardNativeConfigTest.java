package com.getpcpanel.cpp.linux;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import com.getpcpanel.commands.command.CommandKeystroke;

/**
 * Generation-only test: makes the GraalVM tracing agent record the X11 and XTest JNA dynamic
 * proxies that {@code Native.load} builds, so {@code reachability-metadata.json} can be regenerated
 * from a run rather than hand-edited.
 *
 * <p>Disabled by default; runs only under the {@code native-config-gen} Maven profile, which sets
 * {@code pcpanel.generate-native-config=true}. Two distinct loads are needed:
 * <ul>
 *   <li><b>libX11</b> — exercised through the real feature ({@link CommandKeystroke#execute()} ->
 *       {@code KeyMacro} -> {@link LinuxKeyboard}): opening the X display loads libX11. {@code F24}
 *       is used because it has no keycode on a typical keymap, so nothing is actually typed.</li>
 *   <li><b>libXtst</b> — that same property means the feature path never reaches
 *       {@code XTestFakeKeyEvent} (no keycode -> no XTest call), so it would NOT load libXtst. The
 *       only keystroke that does reach XTest is one the host can actually type, which would inject a
 *       real key. To capture the XTest proxy without injecting anything, libXtst is loaded directly
 *       by initialising the interface.</li>
 * </ul>
 * The guarantee that these proxies are <em>registered</em> comes from
 * {@link com.getpcpanel.cpp.ProxyRegistrationCoverageTest}; this test only makes the agent observe
 * them during generation.
 */
@EnabledOnOs(OS.LINUX)
@EnabledIfSystemProperty(named = "pcpanel.generate-native-config", matches = "true")
@DisplayName("Linux keystroke native-config generation")
class LinuxKeyboardNativeConfigTest {

    @Test
    @DisplayName("loads libX11 (via the feature) and libXtst so the agent records both JNA proxies")
    void loadsX11AndXTestForAgent() {
        // libX11: real feature path opens the display. F24 has no keycode -> nothing is typed.
        assertDoesNotThrow(() -> new CommandKeystroke("F24").execute());
        // libXtst: load it directly (see class doc) so XTest is captured regardless of keymap.
        assertDoesNotThrow(() -> Class.forName(
                "com.getpcpanel.cpp.linux.LinuxKeyboard$XTest", true, getClass().getClassLoader()));
    }
}
