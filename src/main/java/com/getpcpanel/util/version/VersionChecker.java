package com.getpcpanel.util.version;

import static com.getpcpanel.util.version.Version.SNAPSHOT_POSTFIX;

import java.util.Comparator;

import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.getpcpanel.profile.SaveService;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Service
@RequiredArgsConstructor
public class VersionChecker extends Thread {
    private final ApplicationEventPublisher eventPublisher;
    private final SaveService save;
    private final RestTemplate webClient;
    @Value("https://api.github.com/repos/${application.github.user-and-repo}/releases?per_page=4") private final String versionCheck;
    @Value("${application.version}") private final String version;
    @Value("${application.build}") private final int build;
    @Value("#{'${application.version}'.endsWith('" + SNAPSHOT_POSTFIX + "')}") private final boolean currentIsSnapshot;

    @PostConstruct
    public void init() {
        setDaemon(true);
        if (save.get().isStartupVersionCheck()) {
            start();
        }
    }

    @Override
    public void run() {
        var response = webClient.getForEntity(versionCheck, Version[].class);
        if (response.getBody() == null) {
            log.error("Unable to get latest version from GitHub");
            return;
        }

        var correctVersion = getVersionOfCorrectType(response.getBody());
        if (versionIsNewer(correctVersion)) {
            updateVersionlabel(correctVersion);
        }
    }

    private Version getVersionOfCorrectType(Version[] versions) {
        return StreamEx.of(versions)
                       .sorted(Comparator.comparing(Version::semVer).reversed()) // Not really nullable
                       .filter(v -> currentIsSnapshot || !v.prerelease()) // Remove snapshots if not currently on one
                       .findFirst()
                       .orElseThrow(() -> new RuntimeException("Unable to find release"));
    }

    private boolean versionIsNewer(Version remoteVersion) {
        var currentSemVer = Version.SemVer.fromName(version);
        var compared = currentSemVer.compareTo(remoteVersion.semVer());
        if (compared == 0) {
            return currentIsSnapshot && build < remoteVersion.getBuild();
        }
        return compared < 0;
    }

    private void updateVersionlabel(Version remoteVersion) {
        eventPublisher.publishEvent(new NewVersionAvailableEvent(remoteVersion));
    }

    boolean isVersionNewer(String current, String latest) {
        if (current.contains("@"))
            return false;

        var currentSnapshot = current.split("-");
        var currentParts = StreamEx.of(currentSnapshot[0].split("\\.")).mapToInt(NumberUtils::toInt).toArray();
        var latestParts = StreamEx.of(latest.split("\\.")).mapToInt(NumberUtils::toInt).toArray();

        for (var i = 0; i < currentParts.length && i < latestParts.length; i++) {
            if (currentParts[i] < latestParts[i]) {
                return true;
            }
            if (currentParts[i] > latestParts[i]) {
                return false;
            }
        }
        if (currentParts.length == latestParts.length) {
            return currentSnapshot.length == 2;
        }
        return currentParts.length < latestParts.length;
    }

    public record NewVersionAvailableEvent(Version version) {
    }
}
