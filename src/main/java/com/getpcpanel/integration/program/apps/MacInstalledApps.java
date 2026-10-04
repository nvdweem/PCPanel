package com.getpcpanel.integration.program.apps;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.platform.MacBuild;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.log4j.Log4j2;

/** The app bundles in {@code /Applications}, {@code /System/Applications} and {@code ~/Applications}, one folder deep. */
@Log4j2
@ApplicationScoped
@MacBuild
public class MacInstalledApps implements InstalledAppScanner {
    @Override
    public List<InstalledApp> scan() {
        return scan(List.of(Path.of("/Applications"), Path.of("/System/Applications"), Path.of(System.getProperty("user.home"), "Applications")));
    }

    static List<InstalledApp> scan(List<Path> roots) {
        var apps = new ArrayList<InstalledApp>();
        for (var root : roots) {
            collect(root, 1, apps);
        }
        return apps;
    }

    private static void collect(Path dir, int depth, List<InstalledApp> apps) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var entries = Files.list(dir)) {
            for (var entry : entries.filter(Files::isDirectory).toList()) {
                var name = entry.getFileName().toString();
                if (name.endsWith(".app")) {
                    var bundle = StringUtils.removeEnd(name, ".app");
                    apps.add(new InstalledApp(bundle, entry.toString(), bundle));
                } else if (depth > 0) {
                    collect(entry, depth - 1, apps);
                }
            }
        } catch (IOException e) {
            log.debug("Unable to list {}", dir, e);
        }
    }
}
