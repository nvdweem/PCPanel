package com.getpcpanel.util;

import java.net.URI;
import java.nio.charset.Charset;

import javax.annotation.PostConstruct;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Service
@RequiredArgsConstructor
public class VersionChecker extends Thread {
    private final ApplicationEventPublisher eventPublisher;
    @Value("${application.versioncheck}") private String versionCheck;
    @Value("${application.releasespage}") private String releasesPage;
    @Value("${application.version}") private String version;

    @PostConstruct
    public void init() {
        start();
    }

    @Override
    public void run() {
        try (var stream = new URI(versionCheck).toURL().openStream()) {
            updateVersionlabel(IOUtils.readLines(stream, Charset.defaultCharset()).get(0));
        } catch (Exception e) {
            log.error("Unable to check version", e);
        }
    }

    private void updateVersionlabel(String latestVersion) {
        if (isVersionNewer(version, latestVersion)) {
            eventPublisher.publishEvent(new NewVersionAvailableEvent(latestVersion, releasesPage));
        }
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

    public record NewVersionAvailableEvent(String version, String url) {
    }
}
