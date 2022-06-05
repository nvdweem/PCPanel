package com.getpcpanel.util;

import java.io.File;

import javax.annotation.PostConstruct;

import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class FileUtil {
    public static final File FILES_ROOT = new File(System.getProperty("user.home"), ".pcpanel");

    @PostConstruct
    void ensureRoot() {
        if (!FILES_ROOT.exists() && !FILES_ROOT.mkdirs()) {
            log.error("Unable to create file root: {}", FILES_ROOT);
        }
    }

    public File getFile(String file) {
        return new File(FILES_ROOT, file);
    }
}
