package com.getpcpanel;

import java.util.Set;

import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.getpcpanel.util.FileChecker;

import javafx.application.Application;

@SpringBootApplication
public class Main {
    public static void main(String[] args) {
        var argSet = Set.of(args);
        if (!argSet.contains("skipfilecheck")) {
            FileChecker.createAndStart();
        }

        Application.launch(MainFX.class, args);
    }
}
