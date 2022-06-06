package com.getpcpanel.util;

import java.io.File;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
public class FileUtil {
    @Value("${application.root}") private final File root;

    @PostConstruct
    void ensureRoot() {
        if (!root.exists() && !root.mkdirs()) {
            log.error("Unable to create file root: {}", root);
        }
    }

    public File getFile(String file) {
        return new File(root, file);
    }
}
