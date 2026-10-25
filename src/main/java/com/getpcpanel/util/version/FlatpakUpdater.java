package com.getpcpanel.util.version;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.util.os.ProcessHelper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * Linux Flatpak self-update. Asks the host (via {@code flatpak-spawn --host}) to {@code flatpak update} the
 * install and branch this instance runs from, which pulls the newest commit from the OSTree remote that the
 * {@code .flatpakref} added at install time, then relaunches that same install and branch on the host.
 *
 * <p>Supported whenever running inside the Flatpak sandbox ({@code $FLATPAK_ID} is set). It only relaunches when
 * the install's commit actually changed: an install from the single-file {@code .flatpak} bundle has no usable
 * remote, so {@code flatpak update} answers "Nothing to do" with exit code 0, and relaunching then would only have
 * the new instance find the same newer release and update again, in a loop. {@code reinstallCurrent()} runs the
 * same path but relaunches regardless, to smoke-test the wiring.
 */
@Log4j2
@ApplicationScoped
public class FlatpakUpdater implements PlatformUpdater {
    private static final Path FLATPAK_INFO = Path.of("/.flatpak-info");

    @Inject
    ProcessHelper processes;

    @Override
    public boolean isSupported() {
        return StringUtils.isNotBlank(System.getenv("FLATPAK_ID"));
    }

    @Override
    public AutoUpdateService.UpdateTarget updateToLatest() throws Exception {
        return runUpdate(true);
    }

    @Override
    public AutoUpdateService.UpdateTarget reinstallCurrent() throws Exception {
        return runUpdate(false);
    }

    private AutoUpdateService.UpdateTarget runUpdate(boolean onlyWhenChanged) throws Exception {
        var appId = System.getenv("FLATPAK_ID");
        if (StringUtils.isBlank(appId)) {
            throw new AutoUpdateService.UpdateException("Flatpak self-update is only available inside the Flatpak sandbox.");
        }
        var install = Install.read(FLATPAK_INFO);

        log.info("Updating Flatpak {} ({}) via the host", install.ref(appId), install);
        var result = processes.run(UPDATE_TIMEOUT, host("update", install.scopeArgs(), "-y", install.ref(appId)));
        if (result.timedOut()) {
            throw new AutoUpdateService.UpdateException("flatpak update did not finish within " + UPDATE_TIMEOUT.toMinutes() + " minutes.");
        }
        if (result.exitCode() != 0) {
            throw new AutoUpdateService.UpdateException(failureMessage(install, appId, result));
        }

        if (onlyWhenChanged && install.commit() != null && install.commit().equals(installedCommit(install, appId))) {
            log.warn("flatpak update left {} at commit {}; not relaunching", install.ref(appId), install.commit());
            throw new AutoUpdateService.UpdateException("Flatpak found no update for this install, so PCPanel was not restarted. "
                    + "An install from the single-file .flatpak has no repository to update from: install PCPanel from its "
                    + "repository instead (see the Flatpak section of linux.md).");
        }

        // Relaunch on the host so it survives the sandbox teardown when this instance exits, and relaunch this same
        // install and branch: a plain `flatpak run` prefers a user install over a system one.
        var relaunch = new ArrayList<>(List.of("flatpak-spawn", "--host", "sh", "-c", "sleep 3; exec flatpak run \"$@\"", "pcpanel-relaunch"));
        relaunch.addAll(install.scopeArgs());
        relaunch.addAll(install.branchArgs());
        relaunch.add(appId);
        UpdaterRestart.relaunchAndExit(processes, relaunch);
        return new AutoUpdateService.UpdateTarget("the latest version", "");
    }

    private @Nullable String installedCommit(Install install, String appId) {
        try {
            var result = processes.run(UPDATE_TIMEOUT, host("info", install.scopeArgs(), "--show-commit", install.ref(appId)));
            return result.succeeded() && !result.stdout().isEmpty() ? result.stdout().getLast().trim() : null;
        } catch (IOException e) {
            log.warn("Could not read the installed Flatpak commit: {}", e.toString());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static String failureMessage(Install install, String appId, ProcessHelper.Result result) {
        var detail = errorLine(result);
        var message = "flatpak update failed" + (detail == null ? " (exit code " + result.exitCode() + ")." : ": " + detail + (detail.endsWith(".") ? "" : "."));
        if (install.scope() == Scope.SYSTEM) {
            // The PCPanel repository is not GPG-signed, and flatpak only pulls from such a remote into the
            // system installation as root.
            message += " A system-wide install of PCPanel can only be updated as root: run `sudo flatpak update --system " + appId + "`.";
        }
        return message;
    }

    /**
     * The line that says what went wrong. A failed {@code flatpak update} prints each operation's {@code Error: …} and
     * then a generic {@code error: There were one or more errors}, so the first {@code Error:} line is preferred over
     * the last line.
     */
    static @Nullable String errorLine(ProcessHelper.Result result) {
        var lines = new ArrayList<>(result.stdout());
        lines.addAll(result.stderr());
        var specific = lines.stream().map(String::trim).filter(l -> l.startsWith("Error:")).findFirst();
        if (specific.isPresent()) {
            return specific.get();
        }
        for (var i = lines.size() - 1; i >= 0; i--) {
            if (StringUtils.isNotBlank(lines.get(i))) {
                return lines.get(i).trim();
            }
        }
        return null;
    }

    /** A {@code flatpak} command on the host, in the C locale so its messages read the same everywhere. */
    private static String[] host(String flatpakCommand, List<String> scopeArgs, String... args) {
        var command = new ArrayList<>(List.of("flatpak-spawn", "--host", "--env=LC_ALL=C", "flatpak", flatpakCommand));
        command.addAll(scopeArgs);
        command.addAll(List.of(args));
        return command.toArray(String[]::new);
    }

    enum Scope { SYSTEM, USER }

    /**
     * What this sandbox runs: the {@code [Instance]} section of {@code /.flatpak-info}. Any part can be null when the
     * file is missing or unusual; the update then falls back to a plain {@code flatpak update <app id>}.
     */
    record Install(@Nullable String commit, @Nullable String branch, @Nullable Scope scope) {
        static Install read(Path file) {
            try {
                return parse(Files.readAllLines(file));
            } catch (IOException e) {
                log.warn("Could not read {}: {}", file, e.toString());
                return new Install(null, null, null);
            }
        }

        static Install parse(List<String> lines) {
            String commit = null, branch = null, appPath = null;
            var inInstance = false;
            for (var raw : lines) {
                var line = raw.trim();
                if (line.startsWith("[")) {
                    inInstance = "[Instance]".equals(line);
                } else if (inInstance && line.contains("=")) {
                    var key = StringUtils.substringBefore(line, "=");
                    var value = StringUtils.trimToNull(StringUtils.substringAfter(line, "="));
                    switch (key) {
                        case "app-commit" -> commit = value;
                        case "branch" -> branch = value;
                        case "app-path" -> appPath = value;
                        default -> { }
                    }
                }
            }
            return new Install(commit, branch, scopeOf(appPath));
        }

        private static @Nullable Scope scopeOf(@Nullable String appPath) {
            if (appPath == null) {
                return null;
            }
            if (appPath.startsWith("/var/lib/flatpak/")) {
                return Scope.SYSTEM;
            }
            return appPath.contains("/.local/share/flatpak/") ? Scope.USER : null;
        }

        String ref(String appId) {
            return branch == null ? appId : appId + "//" + branch;
        }

        List<String> scopeArgs() {
            if (scope == null) {
                return List.of();
            }
            return List.of(scope == Scope.SYSTEM ? "--system" : "--user");
        }

        List<String> branchArgs() {
            return branch == null ? List.of() : List.of("--branch=" + branch);
        }
    }
}
