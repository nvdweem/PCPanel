package com.getpcpanel.util;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class ExtractUtil {
    @Value("${application.version}") private String version;
    @Value("${application.build}") private String build;

    public File extractAndDeleteOnExit(String file) {
        var name = FilenameUtils.getBaseName(file);
        var ext = FilenameUtils.getExtension(file);

        var extracted = new File(System.getProperty("java.io.tmpdir"), String.join(".", name, version, build, ext));
        if (extracted.exists() && !extracted.delete()) {
            log.info("{} already exists, not updating", extracted);
            return extracted;
        }

        try {
            var resource = Util.class.getResource("/" + file);
            FileUtils.copyURLToFile(Objects.requireNonNull(resource), extracted);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        extracted.deleteOnExit();
        return extracted;
    }

}
