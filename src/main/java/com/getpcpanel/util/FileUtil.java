package com.getpcpanel.util;

import java.io.File;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
@ApplicationScoped
public class FileUtil {
    @ConfigProperty(name = "pcpanel.root")
    String rootPath;

    private File root;

    @PostConstruct
    void ensureRoot() {
        root = new File(rootPath);
        log.info("Using root: " + root);
        if (!root.exists() && !root.mkdirs()) {
            log.error("Unable to create file root: " + root);
        }
    }

    public File getFile(String file) {
        return new File(root, file);
    }

    public File getRoot() {
        return root;
    }
}


import java.io.File;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.enterprise.context.ApplicationScoped;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
@ApplicationScoped
@RequiredArgsConstructor
public class FileUtil {
    @ConfigProperty(name="pcpanel.root") private final File root;

    @PostConstruct
    void ensureRoot() {
        log.info("Using root: {}", root);
        if (!root.exists() && !root.mkdirs()) {
            log.error("Unable to create file root: {}", root);
        }
    }

    public File getFile(String file) {
        return new File(root, file);
    }
}
