package com.getpcpanel.rest;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.getpcpanel.util.version.AutoUpdateService;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.log4j.Log4j2;

/**
 * Reports the host platform so the UI can hide platform-specific integrations, plus the running
 * app version (so the UI doesn't hardcode it). This must come from the backend (where the device +
 * integrations actually run), not the browser — the UI may be opened from another machine on the
 * network. Voicemeeter is Windows-only; Elgato Wave Link is Windows/macOS only.
 */
@Log4j2
@jakarta.ws.rs.Path("/api/platform")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class PlatformResource {

    @Inject AutoUpdateService autoUpdate;

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "dev")
    String version;

    /** Resolved once at startup; only set for local (SNAPSHOT) builds, else null. */
    @Nullable private String branch;
    /** Short HEAD commit of a local build, so the running build is identifiable in the UI; null for releases. */
    @Nullable private String commit;

    public record PlatformInfo(String os, boolean voicemeeter, boolean waveLink, boolean flatpak, boolean autoUpdate, String version, @Nullable String branch, @Nullable String commit) {
    }

    @PostConstruct
    void init() {
        // Show the git branch + commit only for local/unreleased (SNAPSHOT) builds, so several dev
        // instances (e.g. one per worktree) can be told apart in the UI and the exact running build is
        // identifiable. Released builds carry a concrete version and report neither.
        if (version != null && version.contains("SNAPSHOT")) {
            branch = detectLocalBranch();
            commit = detectLocalCommit();
        }
    }

    @GET
    public PlatformInfo get() {
        var os = SystemUtils.IS_OS_WINDOWS ? "windows" : SystemUtils.IS_OS_MAC ? "mac" : SystemUtils.IS_OS_LINUX ? "linux" : "other";
        // Running inside the Flatpak sandbox: the UI uses this to warn that Discord's IPC socket is only
        // visible if Discord was already running when PCPanel (and so the sandbox) started.
        var flatpak = StringUtils.isNotBlank(System.getenv("FLATPAK_ID"));
        return new PlatformInfo(os, SystemUtils.IS_OS_WINDOWS, SystemUtils.IS_OS_WINDOWS || SystemUtils.IS_OS_MAC, flatpak, autoUpdate.isSupported(), version, branch, commit);
    }

    /**
     * The working directory's git directory, handling the worktree case where {@code .git} is a file
     * pointing at the real gitdir. Null when not in a git checkout (e.g. an installed build).
     */
    @Nullable
    private static Path gitDir() {
        try {
            var git = Path.of(".git");
            if (!Files.exists(git)) {
                return null;
            }
            if (Files.isDirectory(git)) {
                return git;
            }
            var pointer = Files.readString(git).trim(); // "gitdir: <path>"
            return Path.of(pointer.startsWith("gitdir:") ? pointer.substring("gitdir:".length()).trim() : pointer);
        } catch (Exception e) { // NOSONAR - best-effort dev convenience, never fatal
            log.debug("Could not locate git dir for build label", e);
            return null;
        }
    }

    /** Current git branch (or short sha when detached); null outside a checkout. Best-effort. */
    @Nullable
    private static String detectLocalBranch() {
        var gitDir = gitDir();
        if (gitDir == null) {
            return null;
        }
        try {
            var head = Files.readString(gitDir.resolve("HEAD")).trim();
            if (head.startsWith("ref:")) {
                var ref = head.substring(4).trim(); // refs/heads/<branch> — keep slashes in the branch name
                return ref.startsWith("refs/heads/") ? ref.substring("refs/heads/".length()) : ref;
            }
            return shortSha(head); // detached HEAD → short sha
        } catch (Exception e) { // NOSONAR - best-effort dev convenience, never fatal
            log.debug("Could not determine git branch for build label", e);
            return null;
        }
    }

    /**
     * Short HEAD commit. Resolved from the per-worktree reflog ({@code logs/HEAD}), whose last entry's
     * new-oid is the current commit — this is correct for linked worktrees, where {@code refs/heads/*}
     * live in the shared common dir and may be packed. Null outside a checkout. Best-effort.
     */
    @Nullable
    private static String detectLocalCommit() {
        var gitDir = gitDir();
        if (gitDir == null) {
            return null;
        }
        try {
            var head = Files.readString(gitDir.resolve("HEAD")).trim();
            if (!head.startsWith("ref:")) {
                return shortSha(head); // detached HEAD is already the commit
            }
            var log = gitDir.resolve("logs/HEAD");
            if (Files.exists(log)) {
                var lines = Files.readAllLines(log);
                for (var i = lines.size() - 1; i >= 0; i--) {
                    var parts = lines.get(i).split("\\s+");
                    if (parts.length >= 2 && parts[1].length() >= 7) {
                        return shortSha(parts[1]); // "<old> <new> <author> ..." → new-oid
                    }
                }
            }
            return null;
        } catch (Exception e) { // NOSONAR - best-effort dev convenience, never fatal
            log.debug("Could not determine git commit for build label", e);
            return null;
        }
    }

    @Nullable
    private static String shortSha(String sha) {
        return sha.length() >= 7 ? sha.substring(0, 7) : sha.isBlank() ? null : sha;
    }
}
