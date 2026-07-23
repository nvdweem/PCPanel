package com.getpcpanel.util.version;

import static com.getpcpanel.util.version.Version.SNAPSHOT_POSTFIX;

import java.io.IOException;
import java.net.URI;
import com.getpcpanel.util.SharedHttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Comparator;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.util.version.Version.SemVer;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

// @Singleton (not @ApplicationScoped) on purpose: this bean extends Thread, and a normal-scoped
// bean needs an Arc client proxy that subclasses the bean type. Thread's many final methods can't
// be overridden in that proxy, which logs a "cannot be proxied" warning for each. @Singleton beans
// are not client-proxied (injection hands out the instance directly), so they avoid the noise — which
// is why SystemResource can inject this for the on-demand check without triggering the proxy warnings.
@Log4j2
@Startup
@Singleton
public class VersionChecker extends Thread {
    @Inject Event<Object> eventBus;
    @Inject SaveService save;
    @Inject ObjectMapper objectMapper;
    @Inject AutoUpdateService autoUpdate;

    @ConfigProperty(name = "pcpanel.version")
    String version;

    @ConfigProperty(name = "pcpanel.build")
    int build;

    private boolean currentIsSnapshot;

    @PostConstruct
    public void init() {
        currentIsSnapshot = version.endsWith(SNAPSHOT_POSTFIX);
        setDaemon(true);
        if (save.get().isStartupVersionCheck()) {
            start();
        }
    }

    @Override
    public void run() {
        try {
            var correctVersion = fetchLatestCandidate();
            if (versionIsNewer(correctVersion)) {
                onNewVersion(correctVersion);
            }
        } catch (Exception e) {
            log.error("Unable to get latest version from GitHub", e);
        }
    }

    /**
     * Run an update check immediately and synchronously (off the startup {@link #run()} thread). Unlike
     * startup this never auto-updates — it only reports whether a newer release of the chosen type exists —
     * so the debug "check for updates" button can surface the new-version popup without silently
     * installing. Returns the newer version, or empty when already up to date. Throws on network/parse
     * failure so the caller can report it.
     */
    public Optional<Version> checkForUpdatesNow() throws IOException, InterruptedException {
        var candidate = fetchLatestCandidate();
        return versionIsNewer(candidate) ? Optional.of(candidate) : Optional.empty();
    }

    private Version fetchLatestCandidate() throws IOException, InterruptedException {
        var url = "https://api.github.com/repos/" + UpdateSource.GITHUB_REPO + "/releases?per_page=4";
        var client = SharedHttpClient.get();
        var request = HttpRequest.newBuilder(URI.create(url)).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        var versions = objectMapper.readValue(response.body(), Version[].class);
        return getVersionOfCorrectType(versions);
    }

    /**
     * A newer version of the chosen type is available. If auto-update is enabled and supported (a Windows
     * install), download and silently install it now and let the installer restart the app; otherwise
     * (or if the update could not be started) notify the UI so the user can update manually.
     */
    private void onNewVersion(Version correctVersion) {
        if (save.get().isAutoUpdate() && autoUpdate.isSupported()) {
            try {
                log.info("Auto-updating to {} on startup", correctVersion.versionDisplay());
                autoUpdate.updateToLatest();
                return; // the installer closes and relaunches the app
            } catch (Exception e) {
                log.error("Auto-update on startup failed; falling back to a notification", e);
            }
        }
        eventBus.fire(new NewVersionAvailableEvent(correctVersion));
    }

    // Whether to consider pre-release (snapshot) builds is now an explicit user setting, not derived from
    // the running build's own snapshot-ness (currentIsSnapshot is only used to compare build numbers).
    private Version getVersionOfCorrectType(Version[] versions) {
        return StreamEx.of(versions)
                       .sorted(Comparator.comparing(Version::semVer).reversed())
                       .filter(v -> save.get().isCheckForPreReleases() || !v.prerelease())
                       .findFirst()
                       .orElseThrow(() -> new RuntimeException("Unable to find release"));
    }

    private boolean versionIsNewer(Version remoteVersion) {
        var currentSemVer = SemVer.fromName(version);
        if (currentIsSnapshot) {
            currentSemVer = currentSemVer.withBuild(build);
        }
        var compared = currentSemVer.compareTo(remoteVersion.semVer());
        if (compared == 0) {
            return currentIsSnapshot && build < remoteVersion.getBuild();
        }
        return compared < 0;
    }

    public record NewVersionAvailableEvent(Version version) {
    }
}
