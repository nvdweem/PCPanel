package com.getpcpanel.device.provider.deej;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import lombok.extern.log4j.Log4j2;

/**
 * Works around a jSerialComm 2.10.2 native-loader bug on Windows.
 *
 * <p>When jSerialComm self-extracts its bundled native lib (the plain-JVM path - dev mode and any
 * non-native run), its Windows code tries a hardcoded architecture order
 * {@code [aarch64, armv7, x86_64, x86]} and extracts the <em>first arch whose DLL exists in the
 * jar</em> rather than the one matching {@code os.arch}. The jar ships {@code Windows/aarch64/}, so on
 * an ordinary x86-64 host it extracts the ARM64 DLL and dies with
 * {@code "Can't load ARM 64-bit .dll on a AMD 64-bit platform"} - and because every arch extracts to
 * the same file path it never recovers. Deej serial then reports no ports.
 *
 * <p>The shipped GraalVM native image is unaffected: it can't self-extract (CodeSource is null), so it
 * loads a companion {@code jSerialComm.dll} placed next to the runner via {@code java.library.path}
 * (see the {@code os-windows} packaging profile). This fix therefore targets only the plain-JVM path.
 *
 * <p>The fix: extract the {@code os.arch}-correct DLL ourselves and point jSerialComm at it via its
 * {@code jSerialComm.library.path} property, whose flat-file fallback loads it directly and bypasses
 * the broken arch probing. Must run before {@code SerialPort} is first referenced (its static
 * initializer reads the property) - {@link JSerialCommTransport}'s static block calls this, and the
 * transport is the only class that touches {@code com.fazecast.jSerialComm}.
 */
@Log4j2
final class JSerialCommArchFix {
    private static final String LIBRARY_PATH_PROPERTY = "jSerialComm.library.path";

    private JSerialCommArchFix() {
    }

    static void apply() {
        try {
            // Native image: companion-DLL model handles loading; never self-extracts. Leave it alone.
            if (System.getProperty("org.graalvm.nativeimage.imagecode") != null) {
                return;
            }
            // Respect an explicit override.
            var existing = System.getProperty(LIBRARY_PATH_PROPERTY);
            if (existing != null && !existing.isBlank()) {
                return;
            }
            var osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            // The bad arch ordering is in jSerialComm's Windows branch. The Linux branch already probes
            // x86_64 first, so it is not affected; macOS is best-effort and untested here.
            if (!osName.contains("win")) {
                return;
            }
            var arch = windowsArchSubdir(System.getProperty("os.arch", ""));
            var resource = "/Windows/" + arch + "/jSerialComm.dll";
            try (var in = JSerialCommArchFix.class.getResourceAsStream(resource)) {
                if (in == null) {
                    log.warn("jSerialComm native {} not found on the classpath; leaving jSerialComm to its own loader", resource);
                    return;
                }
                var dir = Path.of(System.getProperty("java.io.tmpdir"), "pcpanel", "jSerialComm", arch);
                Files.createDirectories(dir);
                var dll = dir.resolve("jSerialComm.dll");
                var bytes = in.readAllBytes();
                // Skip the rewrite when it already matches (a running instance may hold a lock on the
                // loaded DLL, which would make the write fail - the existing copy is the correct one).
                if (!Files.exists(dll) || Files.size(dll) != bytes.length) {
                    try {
                        Files.write(dll, bytes);
                    } catch (Exception writeFailed) {
                        if (!Files.exists(dll)) {
                            log.warn("Could not stage jSerialComm native at {}: {}", dll, writeFailed.getMessage());
                            return;
                        }
                    }
                }
                System.setProperty(LIBRARY_PATH_PROPERTY, dir.toString());
                log.info("Pinned jSerialComm native to {} (os.arch={})", dll, System.getProperty("os.arch"));
            }
        } catch (Throwable t) {
            // Never let the workaround break startup; jSerialComm falls back to its own (buggy) loader.
            log.warn("Could not pin jSerialComm architecture, falling back to its bundled loader: {}", t.getMessage());
        }
    }

    /** Maps {@code os.arch} to jSerialComm's Windows native sub-directory name. */
    private static String windowsArchSubdir(String osArch) {
        var arch = osArch.toLowerCase(Locale.ROOT);
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            return "aarch64";
        }
        if (arch.contains("arm")) {
            return "armv7";
        }
        if (arch.contains("amd64") || arch.contains("x86_64") || arch.equals("x64")) {
            return "x86_64";
        }
        if (arch.contains("86")) {
            return "x86";
        }
        return "x86_64"; // sensible default for desktop Windows
    }
}
