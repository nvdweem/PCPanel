package com.getpcpanel.platform;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Forces the GraalVM native image to ship working AWT on Windows.
 *
 * <p>GraalVM emits the JNI/JVM symbol exports in its generated {@code java.dll}/{@code jvm.dll} shim
 * libraries only when its analysis observes a reachable {@code System.loadLibrary("awt")} — see
 * {@code com.oracle.svm.hosted.jdk.JNIRegistrationAWTSupport}, whose shim-export registration is gated
 * on {@code JNIRegistrationSupport.isRegisteredLibrary("awt")}, set by an invocation plugin on
 * {@code System.loadLibrary}/{@code BootLoader.loadLibrary}. For this app that load is reached only
 * transitively through the JDK's headless Java2D init, and the analysis registers it
 * non-deterministically: some builds export the AWT symbols, some link the shims with none. When the
 * shims are empty the JDK's {@code awt.dll} cannot resolve its imports and {@code loadLibrary("awt")}
 * fails at runtime ({@code UnsatisfiedLinkError: Can't load library: awt}), silently disabling the
 * overlay and the font picker.
 *
 * <p>The literal call below is intercepted by that plugin at build time, so "awt" is always
 * registered and the shims always export what {@code awt.dll} (and {@code fontmanager.dll}) import.
 * It is present only in the Windows build ({@link WindowsBuild}); the Linux and macOS native images
 * are AWT-free. At runtime the load is harmless and idempotent — the overlay renderer and process-icon
 * extraction use headless Java2D regardless.
 */
@Startup
@ApplicationScoped
@WindowsBuild
public class WindowsAwtLibrarySupport {
    @PostConstruct
    void ensureAwtLibraryRegistered() {
        // Build-time registration only. GraalVM's System.loadLibrary invocation plugin reads the
        // constant "awt" while parsing this method (regardless of the branch ever running) and
        // registers the JDK library, so JNIRegistrationAWTSupport emits the java.dll/jvm.dll shim
        // exports awt.dll needs. The guard reads a system property native-image cannot fold away, so
        // the branch is kept and the plugin fires — but the property is never set, so the load never
        // runs at runtime. The JDK loads awt itself via headless Java2D; doing it again here under a
        // different caller would risk a redundant/racing native-library load that can poison AWT init.
        if (System.getProperty("pcpanel.force-awt-load.unset") != null) {
            System.loadLibrary("awt");
        }
    }
}
