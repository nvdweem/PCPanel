package com.getpcpanel.integration.keyboard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.program.IPlatformCommand;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Regression guard for the "works in dev, dead in native" trap: a CDI bean that is only ever obtained
 * through {@code CdiHelper.getBean(...)} (never an {@code @Inject}/{@code Instance<>} target) has no
 * injection point, so Arc's unused-bean removal prunes it in the native/prod build — and the lookup
 * then throws {@code UnsatisfiedResolutionException} at runtime. Beans in that position must be
 * {@link Unremovable}.
 *
 * <p>The platform {@link Keyboard} backends (media keys / keystrokes) and {@link IPlatformCommand}
 * backends (launch / end program) are exactly those beans — they are resolved from Jackson-created
 * command objects, which cannot inject. This regressed once when the keyboard logic was refactored out
 * of the command into a {@code Keyboard} backend without carrying the annotation across, silently
 * breaking every media/keystroke button in the shipped build.
 */
@DisplayName("CdiHelper-only platform beans stay @Unremovable")
class KeyboardBeanRetentionTest {
    @Test
    @DisplayName("every @ApplicationScoped Keyboard backend is @Unremovable")
    void keyboardBackendsAreUnremovable() throws Exception {
        assertAllApplicationScopedImplsUnremovable(Keyboard.class);
    }

    @Test
    @DisplayName("every @ApplicationScoped IPlatformCommand backend is @Unremovable")
    void platformCommandBackendsAreUnremovable() throws Exception {
        assertAllApplicationScopedImplsUnremovable(IPlatformCommand.class);
    }

    private static void assertAllApplicationScopedImplsUnremovable(Class<?> contract) throws Exception {
        var impls = scan(c -> contract.isAssignableFrom(c) && c != contract
                && !c.isInterface() && !Modifier.isAbstract(c.getModifiers())
                && c.isAnnotationPresent(ApplicationScoped.class));
        assertFalse(impls.isEmpty(), () -> "no concrete " + contract.getSimpleName() + " beans found — scan is broken");
        for (var impl : impls) {
            assertTrue(impl.isAnnotationPresent(Unremovable.class),
                    () -> impl.getName() + " is only reached via CdiHelper.getBean(" + contract.getSimpleName()
                            + ") and must be @Unremovable, or Arc will prune it in the native/prod build");
        }
    }

    private static Set<Class<?>> scan(java.util.function.Predicate<Class<?>> keep) throws Exception {
        var root = Path.of(Keyboard.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var loader = KeyboardBeanRetentionTest.class.getClassLoader();
        var result = new HashSet<Class<?>>();
        try (Stream<Path> walk = Files.walk(root.resolve("com").resolve("getpcpanel"))) {
            for (var classFile : walk.filter(p -> p.toString().endsWith(".class")).toList()) {
                var binary = root.relativize(classFile).toString();
                binary = binary.substring(0, binary.length() - ".class".length()).replace(java.io.File.separatorChar, '.');
                Class<?> clazz;
                try {
                    clazz = Class.forName(binary, false, loader);
                } catch (Throwable e) {
                    continue;
                }
                if (keep.test(clazz)) {
                    result.add(clazz);
                }
            }
        }
        return result;
    }
}
