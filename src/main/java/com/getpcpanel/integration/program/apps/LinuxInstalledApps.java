package com.getpcpanel.integration.program.apps;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.platform.LinuxBuild;
import com.getpcpanel.util.os.FlatpakHost;
import com.getpcpanel.util.os.ProcessHelper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * The desktop's applications: the {@code .desktop} files in the XDG data dirs and the Flatpak exports. They are read
 * by a shell on the host, which is where they live when PCPanel runs in the Flatpak sandbox.
 */
@Log4j2
@ApplicationScoped
@LinuxBuild
public class LinuxInstalledApps implements InstalledAppScanner {
    /** Starts a file in the listing, followed by the file's path on the same line. */
    static final String RECORD = "\u001e";
    /** Prints each desktop file as a {@link #RECORD} line with its path and then its contents, user dirs first. */
    private static final String LIST_SCRIPT = """
            IFS=:
            for d in "${XDG_DATA_HOME:-$HOME/.local/share}" ${XDG_DATA_DIRS:-/usr/local/share:/usr/share} \
                     "$HOME/.local/share/flatpak/exports/share" /var/lib/flatpak/exports/share; do
              for f in "$d"/applications/*.desktop; do
                [ -f "$f" ] && printf '\036%s\n' "$f" && cat "$f" && echo
              done
            done
            """;

    @Inject ProcessHelper processes;

    @Override
    public List<InstalledApp> scan() {
        try {
            var result = processes.run(Duration.ofSeconds(10), FlatpakHost.command("sh", "-c", LIST_SCRIPT));
            return fromListing(result.stdout());
        } catch (IOException e) {
            log.debug("Unable to list the desktop's applications", e);
            return List.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    /** The apps in a listing; of desktop files with the same id (file name), the first one counts. */
    static List<InstalledApp> fromListing(List<String> listing) {
        var files = new LinkedHashMap<String, List<String>>();
        var paths = new LinkedHashMap<String, String>();
        List<String> current = null;
        for (var line : listing) {
            if (line.startsWith(RECORD)) {
                var path = line.substring(RECORD.length());
                var id = StringUtils.substringAfterLast("/" + path, "/");
                current = files.containsKey(id) ? new ArrayList<>() : files.computeIfAbsent(id, k -> new ArrayList<>());
                paths.putIfAbsent(id, path);
            } else if (current != null) {
                current.add(line);
            }
        }
        return files.entrySet().stream()
                    .map(e -> DesktopEntry.parse(paths.get(e.getKey()), e.getValue()))
                    .filter(Objects::nonNull)
                    .toList();
    }
}
