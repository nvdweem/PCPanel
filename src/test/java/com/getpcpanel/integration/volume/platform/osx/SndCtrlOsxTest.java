package com.getpcpanel.integration.volume.platform.osx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getpcpanel.platform.process.OsxProcessHelper;

import jakarta.enterprise.event.Event;

class SndCtrlOsxTest {

    private CoreAudioWrapper wrapper;
    private OsxProcessHelper processHelper;
    private OsxProcessTapService tapService;
    @SuppressWarnings("unchecked")
    private Event<Object> eventBus;
    private SndCtrlOsx sndCtrl;

    @BeforeEach
    void setUp() {
        wrapper = mock(CoreAudioWrapper.class);
        processHelper = mock(OsxProcessHelper.class);
        tapService = mock(OsxProcessTapService.class);
        eventBus = mock(Event.class);
        sndCtrl = new SndCtrlOsx(wrapper, processHelper, tapService, eventBus);
    }

    @Test
    @DisplayName("pidsFor matches Safari PWA by app name and includes audio-active WebKit GPU PID")
    void pidsForMatchesPwaAndGpuProcess() {
        var pwa = new OsxProcessHelper.RunningApp(
            64765,
            "YT Music",
            "/System/Library/CoreServices/Web App.app/Contents/MacOS/Web App",
            "Foreground",
            Set.of(64765, 64772)
        );
        when(processHelper.listApps()).thenReturn(List.of(pwa));

        // Main process and GPU process both register with Core Audio
        when(wrapper.translatePidToProcessObject(64765)).thenReturn(101);
        when(wrapper.translatePidToProcessObject(64772)).thenReturn(102);

        var pids = sndCtrl.pidsFor("YT Music");

        assertEquals(List.of(64765, 64772), pids);
    }

    @Test
    @DisplayName("pidsFor matches Safari and filters out non-audio coalition PIDs")
    void pidsForMatchesSafariAndFiltersNonAudioCoalitionPids() {
        var safari = new OsxProcessHelper.RunningApp(
            31430,
            "Safari",
            "/Applications/Safari.app/Contents/MacOS/Safari",
            "Foreground",
            Set.of(31430, 31439, 31452, 31454)
        );
        when(processHelper.listApps()).thenReturn(List.of(safari));

        // 31430 is main process, 31452 is GPU process with audio
        when(wrapper.translatePidToProcessObject(31430)).thenReturn(201);
        when(wrapper.translatePidToProcessObject(31452)).thenReturn(202);
        // 31439 (networking) and 31454 (WebContent) have no Core Audio presence
        when(wrapper.translatePidToProcessObject(31439)).thenReturn(0);
        when(wrapper.translatePidToProcessObject(31454)).thenReturn(0);

        var pids = sndCtrl.pidsFor("Safari");

        assertEquals(List.of(31430, 31452), pids);
    }

    @Test
    @DisplayName("pidsFor matches conventional applications by binary name or app name")
    void pidsForMatchesConventionalApplications() {
        var spotify = new OsxProcessHelper.RunningApp(
            12345,
            "Spotify",
            "/Applications/Spotify.app/Contents/MacOS/Spotify",
            "Foreground",
            Set.of(12345)
        );
        when(processHelper.listApps()).thenReturn(List.of(spotify));
        when(wrapper.translatePidToProcessObject(12345)).thenReturn(301);

        assertEquals(List.of(12345), sndCtrl.pidsFor("Spotify"));
        assertEquals(List.of(12345), sndCtrl.pidsFor("/Applications/Spotify.app/Contents/MacOS/Spotify"));
    }

    @Test
    @DisplayName("pidsFor ignores background/UIElement apps during matching")
    void pidsForIgnoresBackgroundAppsDuringMatching() {
        var gpuHelper = new OsxProcessHelper.RunningApp(
            31452,
            "Safari Graphics and Media",
            "/System/Library/Frameworks/WebKit.framework/Versions/A/XPCServices/com.apple.WebKit.GPU.xpc/Contents/MacOS/com.apple.WebKit.GPU",
            "UIElement",
            Set.of()
        );
        when(processHelper.listApps()).thenReturn(List.of(gpuHelper));

        assertTrue(sndCtrl.pidsFor("Safari Graphics and Media").isEmpty());
    }

    @Test
    @DisplayName("pidsFor falls back gracefully when listApps is empty")
    void pidsForFallsBackGracefullyWhenListAppsIsEmpty() {
        when(processHelper.listApps()).thenReturn(List.of());

        // When listApps is empty, pidsFor uses getRunningApplications() fallback
        var pids = sndCtrl.pidsFor("NonExistentApp");
        assertTrue(pids.isEmpty());
    }
}
