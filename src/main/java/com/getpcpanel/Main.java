package com.getpcpanel;

import java.util.Set;

import com.getpcpanel.hid.HidDebug;
import com.getpcpanel.util.FileChecker;

import javafx.application.Application;

// TODO
public class Main {
    static void main(String[] args) {
        var argSet = Set.of(args);
        if (!argSet.contains("skipfilecheck")) {
            FileChecker.createAndStart();
        }

        if (argSet.contains("hiddebug")) {
            new HidDebug().execute();
            return;
        }

        Application.launch(MainFX.class, args);
    }
}
