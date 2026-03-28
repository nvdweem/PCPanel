package com.getpcpanel.util.version;

import static com.getpcpanel.util.version.Version.SNAPSHOT_POSTFIX;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Comparator;

import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.getpcpanel.Json;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.util.version.Version.SemVer;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class VersionChecker {
    private final Event<Object> eventPublisher;
    private final SaveService save;
    private static final HttpClient WEB_CLIENT = HttpClient.newBuilder().build();
    private final Json json;
    @ConfigProperty(name = "application.version") private String version;
    @ConfigProperty(name = "application.build") private int build;
    @ConfigProperty(name = "application.github.user-and-repo") private String userAndRepo;

    @PostConstruct
    public void init() {
        if (save.get().isStartupVersionCheck()) {
            var thread = new Thread(this::run, "VersionCheck");
            thread.setDaemon(true);
            thread.start();
        }
    }

    public void run() {
        var remoteVersions = getRemoteVersions();
        if (ArrayUtils.isEmpty(remoteVersions)) {
            log.error("No remote versions found");
            return;
        }

        var correctVersion = getVersionOfCorrectType(remoteVersions);
        if (versionIsNewer(correctVersion)) {
            updateVersionLabel(correctVersion);
        }
    }

    @Nullable
    private Version[] getRemoteVersions() {
        try {
            var request = HttpRequest.newBuilder(URI.create(getVersionCheck())).GET().build();
            var response = WEB_CLIENT.send(request, BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2 || response.body() == null) {
                log.error("Unable to get latest version from GitHub, status={}", response.statusCode());
                return null;
            }
            return json.read(response.body(), Version[].class);
        } catch (Exception e) {
            log.error("Unable to get latest version from GitHub", e);
            return null;
        }
    }

    private Version getVersionOfCorrectType(Version[] versions) {
        return StreamEx.of(versions)
                       .sorted(Comparator.comparing(Version::semVer).reversed()) // Not really nullable
                       .filter(v -> isCurrentSnapshot() || !v.prerelease()) // Remove snapshots if not currently on one
                       .findFirst()
                       .orElseThrow(() -> new RuntimeException("Unable to find release"));
    }

    private boolean versionIsNewer(Version remoteVersion) {
        var currentSemVer = SemVer.fromName(version);
        if (isCurrentSnapshot()) {
            currentSemVer = currentSemVer.withBuild(build);
        }

        var compared = currentSemVer.compareTo(remoteVersion.semVer());
        if (compared == 0) {
            return isCurrentSnapshot() && build < remoteVersion.getBuild();
        }
        return compared < 0;
    }

    private void updateVersionLabel(Version remoteVersion) {
        eventPublisher.fire(new NewVersionAvailableEvent(remoteVersion));
    }

    private String getVersionCheck() {
        return "https://api.github.com/repos/" + userAndRepo + "/releases?per_page=4";
    }

    private boolean isCurrentSnapshot() {
        return version.endsWith(SNAPSHOT_POSTFIX);
    }

    public record NewVersionAvailableEvent(Version version) {
    }
}
