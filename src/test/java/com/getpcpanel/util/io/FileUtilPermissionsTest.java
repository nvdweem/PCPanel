package com.getpcpanel.util.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileUtilPermissionsTest {
    @TempDir
    Path tmp;

    @Test
    void theDataFolderIsOnlyOpenToItsOwner() throws IOException {
        var dir = Files.createDirectory(tmp.resolve("root"));
        assumeTrue(Files.getFileAttributeView(dir, PosixFileAttributeView.class) != null, "POSIX permissions only");
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxr-xr-x"));

        FileUtil.restrictToOwner(dir);

        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(dir)));
    }

    @Test
    void aFolderAlreadyMorePrivateIsLeftAlone() throws IOException {
        var dir = Files.createDirectory(tmp.resolve("root"));
        assumeTrue(Files.getFileAttributeView(dir, PosixFileAttributeView.class) != null, "POSIX permissions only");
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));

        FileUtil.restrictToOwner(dir);

        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(dir)));
    }
}
