package com.getpcpanel;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

import org.eclipse.microprofile.config.ConfigProvider;

import com.getpcpanel.device.provider.pcpanel.HidDebug;
import com.getpcpanel.util.os.ConsoleSupport;
import com.getpcpanel.util.io.FileChecker;
import com.getpcpanel.util.io.PcPanelRoot;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class Main implements QuarkusApplication {
    private static final String SKIP_FILE_CHECK_ARG = "skipfilecheck";
    private static final String SKIP_FILE_CHECK_PROPERTY = "pcpanel.skip-file-check";
    private static final String POST_INSTALL_PROPERTY = "pcpanel.postinstall";
    private static final String UPDATED_PROPERTY = "pcpanel.updated";

    static void main(String... args) {
        forceHeadlessAwt();
        overrideBakedPathsForNativeImage();
        redirectWorkingDirectoryForNativeImage();
        var argSet = Set.of(args);
        if (argSet.contains(ConsoleSupport.CONSOLE_ARG)) {
            ConsoleSupport.attachConsole(); // Open a console before Quarkus boots so its logs are captured too.
        }
        if (argSet.contains("hiddebug")) {
            new HidDebug().execute();
            return;
        }
        markLaunchIntent(argSet);
        Quarkus.run(Main.class, args);
    }

    /**
     * Run the AWT/Java2D subsystem headless. The app never opens an AWT/Swing window on any platform —
     * it only uses <em>headless</em> Java2D (the Win32 layered-window overlay renderer and process-icon
     * extraction on Windows). The full windowing toolkit ({@code sun.awt.windows.WToolkit}) cannot load
     * its {@code libawt} in the GraalVM native image, so the first {@code BufferedImage} use there fails
     * with {@code NoClassDefFoundError: Could not initialize class java.awt.image.BufferedImage},
     * silently disabling the overlay and process icons.
     *
     * <p>This must be set at <em>runtime</em>: {@code java.awt} is {@code --initialize-at-run-time}, so it
     * reads {@code java.awt.headless} when first touched at run time. The {@code -J-Djava.awt.headless=true}
     * native-image build argument only sets the property on the image <em>builder</em> JVM, not on the
     * produced executable, so it does not make the running image headless. Setting it here — before any
     * AWT class is loaded — does, and is harmless on the JVM/dev and on macOS (no AWT in the image).
     */
    @SuppressWarnings("AccessOfSystemProperties")
    private static void forceHeadlessAwt() {
        System.setProperty("java.awt.headless", "true");
    }

    /**
     * Surface how the app was launched so {@code StartupOnboarding} can react, before {@code Quarkus.run}
     * puts the properties in the config at boot:
     * <ul>
     *   <li>{@code /postinstall} — a fresh interactive install finished. Show the post-install/update
     *       dialog <em>and</em> open the UI in the browser (no tab is open yet).</li>
     *   <li>{@code /updated} — the in-app auto-updater relaunched us after a silent update. Show the same
     *       "just updated" dialog, but do <em>not</em> open a browser: the UI that triggered the update is
     *       already open and simply reconnects. See {@code packaging/windows/pcpanel.iss}.</li>
     * </ul>
     * The bare {@code postinstall} form is also accepted so a non-Windows manual launch can trigger it.
     */
    @SuppressWarnings("AccessOfSystemProperties")
    private static void markLaunchIntent(Set<String> argSet) {
        if (argSet.contains("/postinstall") || argSet.contains("postinstall")) {
            System.setProperty(POST_INSTALL_PROPERTY, "true");
        }
        if (argSet.contains("/updated") || argSet.contains("updated")) {
            System.setProperty(UPDATED_PROPERTY, "true");
        }
    }

    /**
     * In a GraalVM native image Quarkus expands {@code ${user.home}} during the build, so the build
     * machine's home directory (e.g. the CI runner's {@code C:\Users\runneradmin}) gets baked into
     * {@code pcpanel.root} and {@code quarkus.log.file.path}. Re-resolve them at runtime via
     * {@link PcPanelRoot} (which also applies the Linux XDG / Flatpak-sandbox location) and publish
     * them as system properties (config ordinal 400) so they override the baked-in
     * application.properties values (ordinal ~250) before Quarkus reads them. On the JVM
     * {@code ${user.home}} is resolved at runtime already, so this is a native-image-only fix — and
     * every shipped Linux artifact (Flatpak, AppImage, .deb) is a native image, so this is where the
     * XDG-aware location takes effect for users.
     */
    @SuppressWarnings("AccessOfSystemProperties")
    private static void overrideBakedPathsForNativeImage() {
        if (!"runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))) {
            return;
        }
        var root = PcPanelRoot.resolve();
        System.setProperty("pcpanel.root", root.toString());
        System.setProperty("quarkus.log.file.path", root.resolve("logs").resolve("logging.log").toString());
    }

    /**
     * When the native image is launched from the Windows {@code Run} registry key or a logon
     * scheduled task, the process inherits {@code C:\Windows\System32} as its working directory.
     * Quarkus scans {@code <user.dir>/config} for extra config files during boot (SmallRye's
     * {@code ConfigDiagnostic#configFilesFromLocations}), and listing the protected
     * {@code System32\config} directory throws {@link java.nio.file.AccessDeniedException}, which is
     * not swallowed and aborts the whole startup — so the app silently never launches on login, even
     * though a manual launch (whose working directory is the install folder) starts fine. Point
     * {@code user.dir} at the executable's own directory, which has no {@code config} subfolder, so
     * the scan finds nothing and is skipped. Resolving the path from {@link ProcessHandle} keeps this
     * pure-JDK and correct regardless of how the process was started.
     */
    @SuppressWarnings("AccessOfSystemProperties")
    private static void redirectWorkingDirectoryForNativeImage() {
        if (!"runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))) {
            return;
        }
        ProcessHandle.current().info().command()
                     .map(Path::of)
                     .map(Path::getParent)
                     .filter(Objects::nonNull)
                     .ifPresent(dir -> System.setProperty("user.dir", dir.toString()));
    }

    @Override
    public int run(String... args) throws Exception {
        var argSet = Set.of(args);
        var skipFileCheck = argSet.contains(SKIP_FILE_CHECK_ARG) || ConfigProvider.getConfig().getOptionalValue(SKIP_FILE_CHECK_PROPERTY, Boolean.class).orElse(false);
        if (!skipFileCheck) {
            FileChecker.createAndStart();
        }
        Quarkus.waitForExit();
        return 0;
    }
}
