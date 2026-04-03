package com.getpcpanel;

import java.util.Set;

import com.getpcpanel.hid.HidDebug;
import com.getpcpanel.util.FileChecker;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class Main implements QuarkusApplication {
    public static void main(String... args) {
        var argSet = Set.of(args);
        if (!argSet.contains("skipfilecheck")) {
            FileChecker.createAndStart();
        }
        if (argSet.contains("hiddebug")) {
            new HidDebug().execute();
            return;
        }
        Quarkus.run(Main.class, args);
    }

    @Override
    public int run(String... args) throws Exception {
        Quarkus.waitForExit();
        return 0;
    }
}
