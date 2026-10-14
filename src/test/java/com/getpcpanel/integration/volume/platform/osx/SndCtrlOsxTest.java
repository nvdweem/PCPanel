package com.getpcpanel.integration.volume.platform.osx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.volume.platform.MuteType;
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
    @DisplayName("pidsFor strictly isolates multiple simultaneously running Safari PWAs")
    void pidsForIsolatesMultipleSimultaneousPwas() {
        var ytMusic = new OsxProcessHelper.RunningApp(
            64765,
            "YT Music",
            "/System/Library/CoreServices/Web App.app/Contents/MacOS/Web App",
            "Foreground",
            Set.of(64765, 64772)
        );
        var slack = new OsxProcessHelper.RunningApp(
            70001,
            "Slack",
            "/System/Library/CoreServices/Web App.app/Contents/MacOS/Web App",
            "Foreground",
            Set.of(70001, 70002)
        );
        when(processHelper.listApps()).thenReturn(List.of(ytMusic, slack));

        when(wrapper.translatePidToProcessObject(64765)).thenReturn(101);
        when(wrapper.translatePidToProcessObject(64772)).thenReturn(102);
        when(wrapper.translatePidToProcessObject(70001)).thenReturn(201);
        when(wrapper.translatePidToProcessObject(70002)).thenReturn(202);

        // Controlling YT Music must never touch Slack
        var ytPids = sndCtrl.pidsFor("YT Music");
        assertEquals(List.of(64765, 64772), ytPids);
        assertFalse(ytPids.contains(70001));
        assertFalse(ytPids.contains(70002));

        // Controlling Slack must never touch YT Music
        var slackPids = sndCtrl.pidsFor("Slack");
        assertEquals(List.of(70001, 70002), slackPids);
        assertFalse(slackPids.contains(64765));
        assertFalse(slackPids.contains(64772));

        // Generic "Web App" must not inadvertently match all PWAs
        assertTrue(sndCtrl.pidsFor("Web App").isEmpty());
        assertTrue(sndCtrl.pidsFor("/System/Library/CoreServices/Web App.app/Contents/MacOS/Web App").isEmpty());
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
    @DisplayName("getFocusApplication resolves PWA display name and isolates focus mute")
    void getFocusApplicationResolvesPwaDisplayName() {
        var pwa = new OsxProcessHelper.RunningApp(
            64765,
            "YT Music",
            "/System/Library/CoreServices/Web App.app/Contents/MacOS/Web App",
            "Foreground",
            Set.of(64765, 64772)
        );
        var slack = new OsxProcessHelper.RunningApp(
            70001,
            "Slack",
            "/System/Library/CoreServices/Web App.app/Contents/MacOS/Web App",
            "Foreground",
            Set.of(70001, 70002)
        );
        when(processHelper.listApps()).thenReturn(List.of(pwa, slack));
        when(wrapper.translatePidToProcessObject(64765)).thenReturn(101);
        when(wrapper.translatePidToProcessObject(64772)).thenReturn(102);

        // Frontmost is YT Music PWA
        when(processHelper.getFrontmostApp()).thenReturn(new OsxProcessHelper.FrontmostApp(
            64765,
            "YT Music",
            "/System/Library/CoreServices/Web App.app/Contents/MacOS/Web App"
        ));

        // getFocusApplication must return display name for Web Apps, avoiding the generic executable path
        var focusTarget = sndCtrl.getFocusApplication();
        assertEquals("YT Music", focusTarget);

        // Focus mute passing getFocusApplication() must resolve only YT Music's PIDs
        var focusPids = sndCtrl.pidsFor(focusTarget);
        assertEquals(List.of(64765, 64772), focusPids);
        assertFalse(focusPids.contains(70001));

        // Frontmost is conventional app (Safari)
        when(processHelper.getFrontmostApp()).thenReturn(new OsxProcessHelper.FrontmostApp(
            31430,
            "Safari",
            "/Applications/Safari.app/Contents/MacOS/Safari"
        ));
        assertEquals("/Applications/Safari.app/Contents/MacOS/Safari", sndCtrl.getFocusApplication());
    }

    @Test
    @DisplayName("pidsFor queries listApps only once and falls back cleanly without duplicate enumeration")
    void pidsForQueriesListAppsOnlyOnceOnFallback() {
        when(processHelper.listApps()).thenReturn(List.of());

        var pids = sndCtrl.pidsFor("NonExistentApp");
        assertTrue(pids.isEmpty());

        // Must query listApps exactly once (no duplicate call via getRunningApplications)
        verify(processHelper, times(1)).listApps();
    }

    @Test
    @DisplayName("setFocusVolume resolves Safari coalition and sets volume for GPU process")
    void setFocusVolumeControlsSafariAndGpuProcess() {
        var safari = new OsxProcessHelper.RunningApp(
            31430,
            "Safari",
            "/Applications/Safari.app/Contents/MacOS/Safari",
            "Foreground",
            Set.of(31430, 31439, 31452, 31454)
        );
        when(processHelper.getFrontmostApp()).thenReturn(new OsxProcessHelper.FrontmostApp(
            31430,
            "Safari",
            "/Applications/Safari.app/Contents/MacOS/Safari"
        ));
        when(processHelper.listApps()).thenReturn(List.of(safari));

        when(wrapper.translatePidToProcessObject(31430)).thenReturn(201);
        when(wrapper.translatePidToProcessObject(31452)).thenReturn(202);
        when(wrapper.translatePidToProcessObject(31439)).thenReturn(0);
        when(wrapper.translatePidToProcessObject(31454)).thenReturn(0);

        sndCtrl.setFocusVolume(0.5f);

        verify(tapService).setVolume(31430, 0.5f);
        verify(tapService).setVolume(31452, 0.5f);
        verify(tapService, never()).setVolume(31439, 0.5f);
        verify(tapService, never()).setVolume(31454, 0.5f);
    }

    @Test
    @DisplayName("setFocusVolume resolves Safari PWA and isolates other running PWAs")
    void setFocusVolumeControlsPwaAndIsolatesSimultaneousPwas() {
        var ytMusic = new OsxProcessHelper.RunningApp(
            64765,
            "YT Music",
            "/System/Library/CoreServices/Web App.app/Contents/MacOS/Web App",
            "Foreground",
            Set.of(64765, 64772)
        );
        var slack = new OsxProcessHelper.RunningApp(
            70001,
            "Slack",
            "/System/Library/CoreServices/Web App.app/Contents/MacOS/Web App",
            "Foreground",
            Set.of(70001, 70002)
        );
        when(processHelper.getFrontmostApp()).thenReturn(new OsxProcessHelper.FrontmostApp(
            64765,
            "YT Music",
            "/System/Library/CoreServices/Web App.app/Contents/MacOS/Web App"
        ));
        when(processHelper.listApps()).thenReturn(List.of(ytMusic, slack));

        when(wrapper.translatePidToProcessObject(64765)).thenReturn(101);
        when(wrapper.translatePidToProcessObject(64772)).thenReturn(102);
        when(wrapper.translatePidToProcessObject(70001)).thenReturn(201);
        when(wrapper.translatePidToProcessObject(70002)).thenReturn(202);

        sndCtrl.setFocusVolume(0.8f);

        // YT Music main and GPU process are adjusted
        verify(tapService).setVolume(64765, 0.8f);
        verify(tapService).setVolume(64772, 0.8f);

        // Unrelated Slack PWA is completely untouched
        verify(tapService, never()).setVolume(70001, 0.8f);
        verify(tapService, never()).setVolume(70002, 0.8f);
    }

    @Test
    @DisplayName("setFocusVolume falls back to frontmost PID when listApps returns empty")
    void setFocusVolumeFallsBackGracefullyWhenListAppsEmpty() {
        when(processHelper.getFrontmostApp()).thenReturn(new OsxProcessHelper.FrontmostApp(
            55555,
            "Notes",
            "/System/Applications/Notes.app/Contents/MacOS/Notes"
        ));
        when(processHelper.listApps()).thenReturn(List.of());

        sndCtrl.setFocusVolume(0.6f);

        verify(tapService).setVolume(55555, 0.6f);
    }

    @Test
    @DisplayName("setFocusVolume falls back to foregroundPid when frontmostApp is null")
    void setFocusVolumeFallsBackToForegroundPid() {
        when(processHelper.getFrontmostApp()).thenReturn(null);
        when(processHelper.foregroundPid()).thenReturn(OptionalInt.of(9999));

        sndCtrl.setFocusVolume(0.4f);

        verify(tapService).setVolume(9999, 0.4f);
    }

    @Test
    @DisplayName("setFocusVolume does nothing when both frontmostApp and foregroundPid are empty")
    void setFocusVolumeDoesNothingWhenNoForegroundProcess() {
        when(processHelper.getFrontmostApp()).thenReturn(null);
        when(processHelper.foregroundPid()).thenReturn(OptionalInt.empty());

        sndCtrl.setFocusVolume(0.4f);

        verify(tapService, never()).setVolume(anyInt(), anyFloat());
    }

    @Test
    @DisplayName("muteFocus strictly isolates identically-named PWAs by frontmost PID and coalition")
    void muteFocusIsolatesIdenticallyNamedPwasByFrontmostCoalition() {
        var pwa1 = new OsxProcessHelper.RunningApp(
            64765,
            "YT Music",
            "/System/Library/CoreServices/Web App.app/Contents/MacOS/Web App",
            "Foreground",
            "com.apple.Safari.WebApp.AAA",
            Set.of(64765, 64772)
        );
        var pwa2 = new OsxProcessHelper.RunningApp(
            77777,
            "YT Music",
            "/System/Library/CoreServices/Web App.app/Contents/MacOS/Web App",
            "Foreground",
            "com.apple.Safari.WebApp.BBB",
            Set.of(77777, 77778)
        );
        when(processHelper.listApps()).thenReturn(List.of(pwa1, pwa2));
        when(processHelper.getFrontmostApp()).thenReturn(new OsxProcessHelper.FrontmostApp(
            64765,
            "YT Music",
            "/System/Library/CoreServices/Web App.app/Contents/MacOS/Web App",
            "com.apple.Safari.WebApp.AAA"
        ));

        when(wrapper.translatePidToProcessObject(64765)).thenReturn(101);
        when(wrapper.translatePidToProcessObject(64772)).thenReturn(102);
        when(wrapper.translatePidToProcessObject(77777)).thenReturn(201);
        when(wrapper.translatePidToProcessObject(77778)).thenReturn(202);

        // Muting focused app (PWA 1)
        sndCtrl.muteFocus(MuteType.toggle);

        // PWA 1 audio processes are muted
        verify(tapService).setMuted(64765, true);
        verify(tapService).setMuted(64772, true);

        // PWA 2 audio processes are NEVER muted or touched
        verify(tapService, never()).setMuted(77777, true);
        verify(tapService, never()).setMuted(77778, true);

        // Targeted mute by bundleId specifically targets PWA 2
        sndCtrl.muteProcesses(Set.of("com.apple.Safari.WebApp.BBB"), MuteType.toggle);
        verify(tapService).setMuted(77777, true);
        verify(tapService).setMuted(77778, true);
    }

    @Test
    @DisplayName("mute toggle heals mixed initial states and maintains consistent repeated toggles")
    void muteApplicationHealsMixedInitialStateAndMaintainsRepeatedToggles() {
        var safari = new OsxProcessHelper.RunningApp(
            31430,
            "Safari",
            "/Applications/Safari.app/Contents/MacOS/Safari",
            "Foreground",
            "com.apple.Safari",
            Set.of(31430, 31452)
        );
        when(processHelper.listApps()).thenReturn(List.of(safari));
        when(processHelper.getFrontmostApp()).thenReturn(new OsxProcessHelper.FrontmostApp(
            31430,
            "Safari",
            "/Applications/Safari.app/Contents/MacOS/Safari",
            "com.apple.Safari"
        ));
        when(wrapper.translatePidToProcessObject(31430)).thenReturn(101);
        when(wrapper.translatePidToProcessObject(31452)).thenReturn(102);

        // Mixed initial state: 31430 is unmuted, 31452 is muted
        when(tapService.isMuted(31430)).thenReturn(false);
        when(tapService.isMuted(31452)).thenReturn(true);

        // Toggle 1: Since app is not fully muted, toggle must mute both processes
        sndCtrl.muteFocus(MuteType.toggle);
        verify(tapService).setMuted(31430, true);
        verify(tapService).setMuted(31452, true);

        // Toggle 2: App is now recorded as muted, toggle must unmute both processes
        sndCtrl.muteFocus(MuteType.toggle);
        verify(tapService).setMuted(31430, false);
        verify(tapService).setMuted(31452, false);

        // Toggle 3: App is now unmuted, toggle must mute both processes again
        sndCtrl.muteFocus(MuteType.toggle);
        verify(tapService, times(2)).setMuted(31430, true);
        verify(tapService, times(2)).setMuted(31452, true);
    }

    @Test
    @DisplayName("helper replacement inherits application mute state without audio leak")
    void helperReplacementInheritsApplicationMuteState() {
        var safariInitial = new OsxProcessHelper.RunningApp(
            31430,
            "Safari",
            "/Applications/Safari.app/Contents/MacOS/Safari",
            "Foreground",
            "com.apple.Safari",
            Set.of(31430, 31452)
        );
        when(processHelper.listApps()).thenReturn(List.of(safariInitial));
        when(wrapper.translatePidToProcessObject(31430)).thenReturn(101);
        when(wrapper.translatePidToProcessObject(31452)).thenReturn(102);

        // Explicitly mute Safari
        sndCtrl.muteProcesses(Set.of("Safari"), MuteType.mute);
        verify(tapService).setMuted(31430, true);
        verify(tapService).setMuted(31452, true);

        // Helper 31452 dies and replacement helper 31455 spawns in Safari's coalition
        var safariReplaced = new OsxProcessHelper.RunningApp(
            31430,
            "Safari",
            "/Applications/Safari.app/Contents/MacOS/Safari",
            "Foreground",
            "com.apple.Safari",
            Set.of(31430, 31455)
        );
        when(processHelper.listApps()).thenReturn(List.of(safariReplaced));
        when(wrapper.translatePidToProcessObject(31455)).thenReturn(103);
        when(tapService.isMuted(31455)).thenReturn(false);

        // Discovering PIDs for Safari (or setting volume) must automatically mute the replacement helper
        var pids = sndCtrl.pidsFor("Safari");
        assertEquals(List.of(31430, 31455), pids);
        verify(tapService).setMuted(31455, true);

        // Setting volume on the muted application sets gain but does not unmute
        sndCtrl.setProcessVolume("Safari", null, 0.7f);
        verify(tapService).setVolume(31455, 0.7f);
        verify(tapService, never()).setMuted(31455, false);
    }
}
