package com.getpcpanel.util.version;

import static com.getpcpanel.util.version.Version.SNAPSHOT_POSTFIX;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Comparator;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.util.version.Version.SemVer;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@ApplicationScoped
public class VersionChecker extends Thread {
    @Inject Event<Object> eventBus;
    @Inject SaveService save;
    @Inject ObjectMapper objectMapper;

    @ConfigProperty(name = "pcpanel.version")
    String version;

    @ConfigProperty(name = "pcpanel.build")
    int build;

    @ConfigProperty(name = "pcpanel.github.user-and-repo")
    String githubUserAndRepo;

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
            var url = "https://api.github.com/repos/" + githubUserAndRepo + "/releases?per_page=4";
            var client = HttpClient.newHttpClient();
            var request = HttpRequest.newBuilder(URI.create(url)).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            var versions = objectMapper.readValue(response.body(), Version[].class);

            var correctVersion = getVersionOfCorrectType(versions);
            if (versionIsNewer(correctVersion)) {
                eventBus.fire(new NewVersionAvailableEvent(correctVersion));
            }
        } catch (Exception e) {
            log.error("Unable to get latest version from GitHub", e);
        }
    }

    private Version getVersionOfCorrectType(Version[] versions) {
        return StreamEx.of(versions)
                       .sorted(Comparator.comparing(Version::semVer).reversed())
                       .filter(v -> currentIsSnapshot || !v.prerelease())
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
