package com.getpcpanel;

import java.util.Set;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.getpcpanel.hid.HidDebug;
import com.getpcpanel.util.FileChecker;

import javafx.application.Application;
import lombok.extern.log4j.Log4j2;

@Log4j2
@EnableCaching
@EnableScheduling
@SpringBootApplication
public class Main {
    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> log.error("Uncaught exception on thread {}", thread.getName(), throwable));

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
