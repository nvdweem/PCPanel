package com.getpcpanel.util.version;

import static com.getpcpanel.util.version.Version.SNAPSHOT_POSTFIX;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.regex.Pattern;

import org.apache.commons.lang3.SystemUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.util.SharedHttpClient;
import com.getpcpanel.util.version.Version.SemVer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

/**
 * Windows self-update: downloads a GitHub release's Inno Setup installer and runs it silently, so the
 * user gets a one-click "Update &amp; restart" instead of downloading and clicking through the installer
 * by hand.
 *
 * <p>Only supported on a Windows <em>installed</em> build (native image). Elsewhere — Linux/macOS, or a
 * dev/JVM run on Windows — {@link #isSupported()} is false and the UI keeps linking to the release
 * download page instead. The install itself is unattended: the installer keeps the existing install
 * directory (its {@code AppId} matches), {@code TrayServiceWin} closes the running app on the installer's
 * {@code WM_CLOSE}, and the installer relaunches it via its {@code /UPDATE} {@code [Run]} entry (see
 * {@code packaging/windows/pcpanel.iss}).
 */
@Log4j2
@ApplicationScoped
public class AutoUpdateService {
    // Inno Setup silent-install switches: no UI, no message boxes, don't reboot. /UPDATE=1 is our own
    // switch that tells the script to relaunch the app after a silent update (its [Run] "Launch now"
    // entry is skipifsilent and would otherwise not fire).
    private static final String[] SILENT_INSTALL_ARGS = { "/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART", "/UPDATE=1" };
    // The Windows installer asset produced by CI is named "PCPanel-<version>-setup.exe".
    static final Pattern SETUP_ASSET = Pattern.compile("(?i)^pcpanel-.*setup\\.exe$");

    @Inject ObjectMapper objectMapper;

    @ConfigProperty(name = "pcpanel.github.user-and-repo") String githubUserAndRepo;
    @ConfigProperty(name = "pcpanel.version") String version;
    @ConfigProperty(name = "pcpanel.build") int build;

    /** True only where an unattended installer update can run: a Windows native-image install. */
    public boolean isSupported() {
        return SystemUtils.IS_OS_WINDOWS && isNativeImage();
    }

    /** Download and silently install the newest available release, then restart. */
    public UpdateTarget updateToLatest() throws IOException, InterruptedException {
        return performUpdate(resolveTarget(true));
    }

    /**
     * Debug/testing: re-download and reinstall the <em>currently running</em> version through the exact
     * same path, so the update mechanism can be exercised without waiting for a newer release.
     */
    public UpdateTarget reinstallCurrent() throws IOException, InterruptedException {
        return performUpdate(resolveTarget(false));
    }

    /** The release we are about to install: its display version and the installer download URL. */
    @io.quarkus.runtime.annotations.RegisterForReflection
    public record UpdateTarget(String version, String installerUrl) {}

    /**
     * Pick the release to install and its Windows installer asset. {@code latest=true} chooses the newest
     * release of the matching type (stable for a release build, including pre-releases for a snapshot);
     * {@code latest=false} chooses the release that matches the currently running version.
     */
    private UpdateTarget resolveTarget(boolean latest) throws IOException, InterruptedException {
        if (!isSupported()) {
            throw new UpdateException("Automatic updates are only available on an installed Windows build.");
        }

        var url = "https://api.github.com/repos/" + githubUserAndRepo + "/releases?per_page=20";
        var request = HttpRequest.newBuilder(URI.create(url)).header("Accept", "application/vnd.github+json").build();
        var response = SharedHttpClient.get().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new UpdateException("GitHub returned HTTP " + response.statusCode() + " while looking for the installer.");
        }

        var root = objectMapper.readTree(response.body());
        var releases = objectMapper.treeToValue(root, Version[].class);
        var target = chooseTarget(releases, latest, isSnapshot(), currentSemVer(), build);
        if (target == null) {
            throw new UpdateException(latest
                    ? "No suitable release was found to update to."
                    : "Could not find a release matching the running version (" + versionDisplay() + ") to reinstall.");
        }

        var installerUrl = findInstallerAsset(root, target.id());
        if (installerUrl == null) {
            throw new UpdateException("Release " + target.versionDisplay() + " has no Windows installer to download.");
        }
        return new UpdateTarget(target.versionDisplay(), installerUrl);
    }

    /**
     * Pick the release to install. {@code latest} = update to the newest available; otherwise reinstall
     * the current version. Snapshot builds share one rolling pre-release ({@code latest-main}), so there
     * is no per-build release to match exactly — both paths target the newest pre-release for a snapshot.
     * A release build has a named release per version, so "reinstall current" matches it exactly.
     */
    static Version chooseTarget(Version[] releases, boolean latest, boolean snapshot, SemVer currentSemVer, int build) {
        if (latest || snapshot) {
            return selectLatest(releases, snapshot);
        }
        return selectCurrent(releases, currentSemVer, snapshot, build);
    }

    /** Newest release of the matching type: stable-only for a release build, pre-releases too for a snapshot. */
    static Version selectLatest(Version[] releases, boolean snapshot) {
        return StreamEx.of(releases)
                       .sorted(Comparator.comparing(Version::semVer).reversed())
                       .filter(v -> snapshot || !v.prerelease())
                       .findFirst()
                       .orElse(null);
    }

    /** The release matching the running version (same semver, and same build number for a snapshot). */
    static Version selectCurrent(Version[] releases, SemVer currentSemVer, boolean snapshot, int build) {
        return StreamEx.of(releases)
                       .filter(v -> v.semVer().compareTo(currentSemVer) == 0 && (!snapshot || v.getBuild() == build))
                       .findFirst()
                       .orElse(null);
    }

    /** The {@code browser_download_url} of the setup asset in the release with the given id, or null. */
    private String findInstallerAsset(JsonNode releasesRoot, int releaseId) {
        for (var release : releasesRoot) {
            if (release.path("id").asInt() != releaseId) {
                continue;
            }
            for (var asset : release.path("assets")) {
                var name = asset.path("name").asText("");
                if (SETUP_ASSET.matcher(name).matches()) {
                    return asset.path("browser_download_url").asText(null);
                }
            }
        }
        return null;
    }

    private UpdateTarget performUpdate(UpdateTarget target) throws IOException, InterruptedException {
        var installer = downloadInstaller(URI.create(target.installerUrl()));
        log.info("Update {} downloaded to {}; launching the silent installer", target.version(), installer);

        var command = StreamEx.of(installer.toString()).append(SILENT_INSTALL_ARGS).toList();
        // Start detached: the installer must outlive this process, which it closes (WM_CLOSE) and then
        // relaunches. We do not wait for it.
        new ProcessBuilder(command)
                .directory(installer.getParent().toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        return target;
    }

    private Path downloadInstaller(URI installerUrl) throws IOException, InterruptedException {
        // Not deleteOnExit: the installer runs from this file and we exit moments later (it closes us), so
        // deleting it on shutdown could race the installer still reading it. The OS cleans %TEMP% instead.
        var dir = Files.createTempDirectory("pcpanel-update-");
        var fileName = installerName(installerUrl);
        var target = dir.resolve(fileName);

        // GitHub asset URLs redirect to a CDN, so this client must follow redirects (the shared client
        // does not). Built lazily here rather than as a static field to keep it out of the native-image
        // build heap.
        var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        var request = HttpRequest.newBuilder(installerUrl).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() / 100 != 2) {
            throw new UpdateException("Downloading the installer failed with HTTP " + response.statusCode() + ".");
        }
        try (var in = response.body()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private static String installerName(URI installerUrl) {
        var path = installerUrl.getPath();
        var name = path.substring(path.lastIndexOf('/') + 1);
        return name.toLowerCase().endsWith(".exe") ? name : "PCPanel-setup.exe";
    }

    private SemVer currentSemVer() {
        var semVer = SemVer.fromName(version);
        return isSnapshot() ? semVer.withBuild(build) : semVer;
    }

    private boolean isSnapshot() {
        return version.endsWith(SNAPSHOT_POSTFIX);
    }

    private String versionDisplay() {
        return isSnapshot() ? version + " (" + build + ")" : version;
    }

    @SuppressWarnings("AccessOfSystemProperties")
    private static boolean isNativeImage() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    /** A user-facing update failure; its message is surfaced to the UI. */
    public static class UpdateException extends RuntimeException {
        public UpdateException(String message) {
            super(message);
        }
    }
}
