package com.getpcpanel.util.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;

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
        restrictToOwner(root.toPath());
    }

    private static final Set<PosixFilePermission> OWNER_ONLY = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);

    /**
     * Keeps other accounts on the machine out of the data folder, which holds integration credentials (MQTT, Home
     * Assistant, Discord, OBS) in {@code profiles.json} and its backups, plus logs and bug reports. Only on file
     * systems with POSIX permissions; Windows keeps a user's profile folder private through its ACLs already.
     */
    static void restrictToOwner(Path dir) {
        var view = Files.getFileAttributeView(dir, PosixFileAttributeView.class);
        if (view == null) {
            return;
        }
        try {
            var permissions = view.readAttributes().permissions();
            if (OWNER_ONLY.containsAll(permissions)) {
                return;
            }
            view.setPermissions(OWNER_ONLY);
            log.info("Restricted {} to its owner (was {})", dir, PosixFilePermissions.toString(permissions));
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            log.warn("Unable to restrict {} to its owner", dir, e);
        }
    }

    public File getFile(String file) {
        return new File(root, file);
    }

    public File getRoot() {
        return root;
    }
}
