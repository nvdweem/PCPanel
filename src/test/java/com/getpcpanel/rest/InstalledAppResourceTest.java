package com.getpcpanel.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.program.apps.InstalledApp;
import com.getpcpanel.integration.program.apps.InstalledAppScanner;
import com.getpcpanel.rest.model.dto.InstalledAppDto;

class InstalledAppResourceTest {
    private int scans;
    private InstalledAppResource resource;

    @BeforeEach
    void setUp() {
        scans = 0;
        InstalledAppScanner scanner = () -> {
            scans++;
            return List.of(
                    new InstalledApp("Zoom", "C:\\all\\Zoom.lnk", "Zoom.exe"),
                    new InstalledApp("Audacity", "C:\\user\\Audacity.lnk", "audacity.exe"),
                    new InstalledApp("Audacity", "C:\\all\\Audacity.lnk", "audacity.exe"));
        };
        resource = new InstalledAppResource(scanner, (w, h, file) -> null);
    }

    @Test
    void listsInstalledAppsSortedWithoutDuplicates() {
        assertEquals(List.of(
                new InstalledAppDto("Audacity", "C:\\user\\Audacity.lnk", "audacity.exe", null),
                new InstalledAppDto("Zoom", "C:\\all\\Zoom.lnk", "Zoom.exe", null)),
                resource.listInstalled());
    }

    @Test
    void aSecondListReusesTheScan() {
        resource.listInstalled();
        resource.listInstalled();

        assertEquals(1, scans);
    }
}
