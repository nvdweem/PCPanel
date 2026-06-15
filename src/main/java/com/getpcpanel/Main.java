package com.getpcpanel;

import java.nio.file.Path;
import java.util.Set;

import org.eclipse.microprofile.config.ConfigProvider;

import com.getpcpanel.hid.HidDebug;
import com.getpcpanel.util.ConsoleSupport;
import com.getpcpanel.util.FileChecker;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class Main implements QuarkusApplication {
    private static final String SKIP_FILE_CHECK_ARG = "skipfilecheck";
    private static final String SKIP_FILE_CHECK_PROPERTY = "pcpanel.skip-file-check";

    static void main(String... args) {
        overrideBakedPathsForNativeImage();
        var argSet = Set.of(args);
        if (argSet.contains(ConsoleSupport.CONSOLE_ARG)) {
            ConsoleSupport.attachConsole(); // Open a console before Quarkus boots so its logs are captured too.
        }
        if (argSet.contains("hiddebug")) {
            new HidDebug().execute();
            return;
        }
        Quarkus.run(Main.class, args);
    }

    /**
     * In a GraalVM native image Quarkus expands {@code ${user.home}} during the build, so the build
     * machine's home directory (e.g. the CI runner's {@code C:\Users\runneradmin}) gets baked into
     * {@code pcpanel.root} and {@code quarkus.log.file.path}. Re-resolve them from the real
     * {@code user.home} at runtime and publish them as system properties (config ordinal 400) so they
     * override the baked-in application.properties values (ordinal ~250) before Quarkus reads them.
     * On the JVM {@code ${user.home}} is resolved at runtime already, so this is a native-image-only fix.
     */
    @SuppressWarnings("AccessOfSystemProperties")
    private static void overrideBakedPathsForNativeImage() {
        if (!"runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))) {
            return;
        }
        var root = Path.of(System.getProperty("user.home"), ".pcpanel");
        System.setProperty("pcpanel.root", root.toString());
        System.setProperty("quarkus.log.file.path", root.resolve("logs").resolve("logging.log").toString());
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
