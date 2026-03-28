package com.getpcpanel.util;

import java.io.File;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class FileUtil {
    @ConfigProperty(name = "application.root") @Setter private File root;

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
