package com.getpcpanel;

import java.util.Set;

import org.eclipse.microprofile.config.ConfigProvider;

import com.getpcpanel.hid.HidDebug;
import com.getpcpanel.util.FileChecker;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class Main implements QuarkusApplication {
    private static final String SKIP_FILE_CHECK_ARG = "skipfilecheck";
    private static final String SKIP_FILE_CHECK_PROPERTY = "pcpanel.skip-file-check";

    static void main(String... args) {
        var argSet = Set.of(args);
        if (argSet.contains("hiddebug")) {
            new HidDebug().execute();
            return;
        }
        Quarkus.run(Main.class, args);
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
