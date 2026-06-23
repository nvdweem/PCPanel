package com.getpcpanel.rest;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.lang3.SystemUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
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

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "dev")
    String version;

    /** Resolved once at startup; only set for local (SNAPSHOT) builds, else null. */
    @Nullable private String branch;

    public record PlatformInfo(String os, boolean voicemeeter, boolean waveLink, String version, @Nullable String branch) {
    }

    @PostConstruct
    void init() {
        // Show the git branch only for local/unreleased (SNAPSHOT) builds, so several dev instances
        // (e.g. one per worktree) can be told apart in the UI. Released builds carry a concrete
        // version and report no branch.
        branch = version != null && version.contains("SNAPSHOT") ? detectLocalBranch() : null;
    }

    @GET
    public PlatformInfo get() {
        var os = SystemUtils.IS_OS_WINDOWS ? "windows" : SystemUtils.IS_OS_MAC ? "mac" : SystemUtils.IS_OS_LINUX ? "linux" : "other";
        return new PlatformInfo(os, SystemUtils.IS_OS_WINDOWS, SystemUtils.IS_OS_WINDOWS || SystemUtils.IS_OS_MAC, version, branch);
    }

    /**
     * Reads the current git branch from the working directory's {@code .git}, handling the worktree
     * case where {@code .git} is a file pointing at the real gitdir. Returns null when not in a git
     * checkout (e.g. an installed build) or on any error — this is a best-effort dev convenience.
     */
    @Nullable
    private static String detectLocalBranch() {
        try {
            var git = Path.of(".git");
            if (!Files.exists(git)) {
                return null;
            }
            Path gitDir;
            if (Files.isDirectory(git)) {
                gitDir = git;
            } else {
                var pointer = Files.readString(git).trim(); // "gitdir: <path>"
                gitDir = Path.of(pointer.startsWith("gitdir:") ? pointer.substring("gitdir:".length()).trim() : pointer);
            }
            var head = Files.readString(gitDir.resolve("HEAD")).trim();
            if (head.startsWith("ref:")) {
                var ref = head.substring(4).trim(); // refs/heads/<branch> — keep slashes in the branch name
                return ref.startsWith("refs/heads/") ? ref.substring("refs/heads/".length()) : ref;
            }
            return head.length() >= 7 ? head.substring(0, 7) : head; // detached HEAD → short sha
        } catch (Exception e) { // NOSONAR - branch label is a best-effort dev convenience, never fatal
            log.debug("Could not determine git branch for build label", e);
            return null;
        }
    }
}
