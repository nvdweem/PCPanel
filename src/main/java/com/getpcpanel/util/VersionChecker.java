package com.getpcpanel.util;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.math.NumberUtils;

import com.getpcpanel.Main;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
public final class VersionChecker extends Thread {
    private final Pane versionTarget;

    public static void init(Pane versionLabel) {
        new VersionChecker(versionLabel).start();
    }

    private VersionChecker(Pane versionLabel) {
        versionTarget = versionLabel;
    }

    @Override
    public void run() {
        try (var stream = new URI(Main.applicationProperties.getProperty("application.versioncheck")).toURL().openStream()) {
            updateVersionlabel(IOUtils.readLines(stream, Charset.defaultCharset()).get(0));
        } catch (Exception e) {
            log.error("Unable to check version", e);
        }
    }

    private void updateVersionlabel(String latestVersion) {
        if (isVersionNewer(Main.VERSION, latestVersion)) {
            var label = new Label("New version available: " + latestVersion);
            label.setStyle("-fx-text-fill: #ff8888; -fx-font-weight: bold;");
            label.setOnMouseClicked(e -> {
                try {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                        Desktop.getDesktop().browse(new URI(Main.applicationProperties.getProperty("application.releasespage")));
                    }
                } catch (URISyntaxException | IOException ex) {
                    log.error("Unable to open browser", ex);
                }
            });
            Platform.runLater(() -> versionTarget.getChildren().add(label));
        }
    }

    static boolean isVersionNewer(String current, String latest) {
        if (Main.UNKNOWN_VERSION.equals(current))
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
}
