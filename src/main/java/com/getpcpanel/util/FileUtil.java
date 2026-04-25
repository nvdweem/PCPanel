package com.getpcpanel.util;

import java.io.File;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.log4j.Log4j2;

@Log4j2
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
