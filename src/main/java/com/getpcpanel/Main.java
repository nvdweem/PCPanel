package com.getpcpanel;

import java.util.Set;

import org.eclipse.microprofile.config.ConfigProvider;

import com.getpcpanel.hid.HidDebug;
import com.getpcpanel.util.ConsoleSupport;
import com.getpcpanel.util.FileChecker;
import com.getpcpanel.util.PCPanelHome;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class Main implements QuarkusApplication {
    private static final String SKIP_FILE_CHECK_ARG = "skipfilecheck";
    private static final String SKIP_FILE_CHECK_PROPERTY = "pcpanel.skip-file-check";

    static void main(String... args) {
        seedDataRootForNativeImage();
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
     * In a native image the {@code ${user.home}} in {@code pcpanel.root} (and therefore the log file path) was
     * expanded at build time to the build machine's home, so config saved against it never persists on the
     * user's machine. Re-resolve it from the live environment and feed it back as a system property (ordinal
     * 400, so it wins over the frozen {@code application.properties} value) before Quarkus reads its config.
     * <p>
     * Only done in a native image: on the JVM (dev mode, the .deb) {@code ${user.home}} resolves correctly at
     * run time, and overriding here would clobber the {@code %dev} {@code .pcpaneldev} root.
     */
    @SuppressWarnings("AccessOfSystemProperties")
    private static void seedDataRootForNativeImage() {
        if (!"runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))) {
            return;
        }
        if (System.getProperty("pcpanel.root") == null) {
            System.setProperty("pcpanel.root", PCPanelHome.resolve().getPath() + "/");
        }
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
