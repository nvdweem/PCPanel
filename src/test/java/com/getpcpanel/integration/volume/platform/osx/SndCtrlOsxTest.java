package com.getpcpanel.integration.volume.platform.osx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

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
    private MockedStatic<ProcessIdentity> identities;
    private MockedStatic<ProcessTap> tapFactory;
    private MockedStatic<OsxProcessTapService> support;
    private Map<ProcessIdentity, Runnable> exits;
    private List<ProcessTap> createdTaps;

    @BeforeEach
    void setUp() {
        wrapper = mock(CoreAudioWrapper.class);
        processHelper = mock(OsxProcessHelper.class);
        identities = mockStatic(ProcessIdentity.class);
        identities.when(() -> ProcessIdentity.find(anyInt()))
                .thenAnswer(call -> new ProcessIdentity(call.getArgument(0), Instant.EPOCH));
        exits = new HashMap<>();
        identities.when(() -> ProcessIdentity.onExit(any(ProcessIdentity.class), any(Runnable.class))).thenAnswer(call -> {
            exits.put(call.getArgument(0), call.getArgument(1));
            return null;
        });
        support = mockStatic(OsxProcessTapService.class, CALLS_REAL_METHODS);
        support.when(OsxProcessTapService::isSupported).thenReturn(true);
        createdTaps = new ArrayList<>();
        tapFactory = mockStatic(ProcessTap.class);
        tapFactory.when(() -> ProcessTap.create(anyInt(), anyString(), anyFloat())).thenAnswer(call -> {
            var tap = mock(ProcessTap.class);
            createdTaps.add(tap);
            var muted = new AtomicBoolean();
            var gain = new AtomicReference<Float>(call.getArgument(2));
            when(tap.muted()).thenAnswer(ignored -> muted.get());
            when(tap.gain()).thenAnswer(ignored -> gain.get());
            doAnswer(change -> { muted.set(change.getArgument(0)); return null; }).when(tap).setMuted(anyBoolean());
            doAnswer(change -> { gain.set(change.getArgument(0)); return null; }).when(tap).setGain(anyFloat());
            return tap;
        });
        when(wrapper.deviceUid(anyInt())).thenReturn("output");
        tapService = spy(new OsxProcessTapService(wrapper));
        eventBus = mock(Event.class);
        sndCtrl = new SndCtrlOsx(wrapper, processHelper, tapService, eventBus);
    }

    @AfterEach
    void tearDown() {
        tapService.destroy();
        tapFactory.close();
        identities.close();
        support.close();
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

        try (var processes = mockStatic(ProcessHandle.class)) {
            processes.when(ProcessHandle::allProcesses).thenAnswer(call -> java.util.stream.Stream.empty());
            assertTrue(sndCtrl.pidsFor("NonExistentApp").isEmpty());
        }

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

        verify(tapService).setApplicationVolume(31430, List.of(31430, 31452), 0.5f);
        tapFactory.verify(() -> ProcessTap.create(0, "output", 0.5f), never());
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
        verify(tapService).setApplicationVolume(64765, List.of(64765, 64772), 0.8f);

        // Unrelated Slack PWA is completely untouched
        tapFactory.verify(() -> ProcessTap.create(201, "output", 0.8f), never());
        tapFactory.verify(() -> ProcessTap.create(202, "output", 0.8f), never());
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

        verify(tapService).setApplicationVolume(55555, List.of(55555), 0.6f);
    }

    @Test
    @DisplayName("failed foreground detection is resolved once and performs no volume operation")
    void setFocusVolumeDoesNothingWhenNoForegroundProcess() {
        when(processHelper.getFrontmostApp()).thenReturn(null);
        sndCtrl.setFocusVolume(0.4f);
        verify(processHelper).getFrontmostApp();
        verify(processHelper, never()).foregroundPid();
        verify(tapService, never()).setApplicationVolume(anyInt(), anyList(), anyFloat());
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
        assertTrue(tapService.isMuted(64765));
        assertTrue(tapService.isMuted(64772));

        // PWA 2 audio processes are NEVER muted or touched
        assertFalse(tapService.isMuted(77777));
        assertFalse(tapService.isMuted(77778));

        // Targeted mute by bundleId specifically targets PWA 2
        sndCtrl.muteProcesses(Set.of("com.apple.Safari.WebApp.BBB"), MuteType.toggle);
        assertTrue(tapService.isMuted(77777));
        assertTrue(tapService.isMuted(77778));
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
        tapService.setMuted(31452, true);
        clearInvocations(tapService);

        // Mixed states converge, and each following toggle changes both actual taps.
        sndCtrl.muteFocus(MuteType.toggle);
        assertTrue(tapService.isMuted(31430));
        assertTrue(tapService.isMuted(31452));
        sndCtrl.muteFocus(MuteType.toggle);
        assertFalse(tapService.isMuted(31430));
        assertFalse(tapService.isMuted(31452));
        sndCtrl.muteFocus(MuteType.toggle);
        assertTrue(tapService.isMuted(31430));
        assertTrue(tapService.isMuted(31452));
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
        assertTrue(tapService.isMuted(31430));
        assertTrue(tapService.isMuted(31452));

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

        // Discovering PIDs for Safari (or setting volume) must automatically mute the replacement helper
        var pids = sndCtrl.pidsFor("Safari");
        assertEquals(List.of(31430, 31455), pids);
        assertTrue(tapService.isMuted(31455));

        // Setting volume on the muted application sets gain but does not unmute
        sndCtrl.setProcessVolume("Safari", null, 0.7f);
        verify(tapService).setApplicationVolume(31430, List.of(31430, 31455), 0.7f);
        assertTrue(tapService.isMuted(31455));
    }
    @Test
    void failedHelperMuteIsRetriedOnNextResolution() {
        var app = new OsxProcessHelper.RunningApp(100, "Safari", "/Applications/Safari.app/Contents/MacOS/Safari",
                "Foreground", "com.apple.Safari", Set.of(100, 101));
        when(processHelper.listApps()).thenReturn(List.of(app));
        when(wrapper.translatePidToProcessObject(101)).thenReturn(1);
        var failure = new ProcessTap.TapCreationException("create", -1);
        tapFactory.when(() -> ProcessTap.create(1, "output", 1f)).thenThrow(failure);
        sndCtrl.muteProcesses(Set.of("Safari"), MuteType.mute);
        // A failed tap leaves actual mute false. Discovery must try again.
        sndCtrl.pidsFor("Safari");
        tapFactory.verify(() -> ProcessTap.create(1, "output", 1f), times(2));
    }

    @Test
    void unrelatedApplicationDoesNotInheritReusedPidMute() {
        var oldApp = new OsxProcessHelper.RunningApp(100, "Safari", "/Applications/Safari.app/Contents/MacOS/Safari",
                "Foreground", "com.apple.Safari", Set.of(100));
        when(wrapper.translatePidToProcessObject(100)).thenReturn(1);
        sndCtrl.muteApp(oldApp, MuteType.mute);
        identities.when(() -> ProcessIdentity.find(100)).thenReturn(new ProcessIdentity(100, Instant.EPOCH.plusSeconds(1)));
        var newApp = new OsxProcessHelper.RunningApp(100, "Music", "/Applications/Music.app/Contents/MacOS/Music",
                "Foreground", "com.apple.Music", Set.of(100, 102));
        when(wrapper.translatePidToProcessObject(102)).thenReturn(2);
        sndCtrl.audioPidsFor(newApp);
        assertFalse(tapService.isMuted(102));
    }

    @Test
    void failedInitialMuteToggleRetriesInsteadOfUnmuting() {
        when(wrapper.translatePidToProcessObject(101)).thenReturn(1);
        var failure = new ProcessTap.TapCreationException("create", -1);
        tapFactory.when(() -> ProcessTap.create(1, "output", 1f)).thenThrow(failure);
        tapService.muteApplication(100, List.of(100, 101), MuteType.toggle);
        assertFalse(tapService.isMuted(101));
        // Restore the mocked factory without loading native audio libraries.
        var recovered = mock(ProcessTap.class);
        when(recovered.muted()).thenReturn(true);
        when(recovered.gain()).thenReturn(1f);
        tapFactory.when(() -> ProcessTap.create(1, "output", 1f)).thenReturn(recovered);
        tapService.muteApplication(100, List.of(100, 101), MuteType.toggle);
        assertTrue(tapService.isMuted(101));
        verify(recovered).setMuted(true);
        verify(recovered, never()).setMuted(false);
    }

    @Test
    void pendingMuteSurvivesFailedCreationAndRecoversWithVolume() {
        when(wrapper.translatePidToProcessObject(101)).thenReturn(1);
        var failure = new ProcessTap.TapCreationException("create", -1);
        tapFactory.when(() -> ProcessTap.create(1, "output", 1f)).thenThrow(failure);
        tapService.muteApplication(100, List.of(100, 101), MuteType.mute);
        assertFalse(tapService.isMuted(101));
        tapService.setApplicationVolume(100, List.of(100, 101), 0.6f);
        assertTrue(tapService.isMuted(101));
        // The main GUI process has no CoreAudio object; it still anchors helper mute intent.
        assertFalse(tapService.isMuted(100));
    }

    @Test
    void outputDeviceRecreationFailureIsRetriedWithMuteIntent() {
        when(wrapper.translatePidToProcessObject(101)).thenReturn(1);
        tapService.muteApplication(100, List.of(100, 101), MuteType.mute);
        assertTrue(tapService.isMuted(101));
        when(wrapper.getDefaultDevice(true)).thenReturn(2);
        when(wrapper.deviceUid(2)).thenReturn("new-output");
        var failure = new ProcessTap.TapCreationException("create", -1);
        tapFactory.when(() -> ProcessTap.create(1, "new-output", 0.5f)).thenThrow(failure);
        tapService.setApplicationVolume(100, List.of(100, 101), 0.5f);
        assertFalse(tapService.isMuted(101));
        tapService.restoreApplicationMute(100, List.of(100, 101));
        assertTrue(tapService.isMuted(101));
    }

    @Test
    void reusedHelperPidDoesNotInheritMuteButReplacementInSameApplicationDoes() {
        when(wrapper.translatePidToProcessObject(101)).thenReturn(1);
        tapService.muteApplication(100, List.of(100, 101), MuteType.mute);
        identities.when(() -> ProcessIdentity.find(101)).thenReturn(new ProcessIdentity(101, Instant.EPOCH.plusSeconds(1)));
        assertFalse(tapService.isMuted(101));
        tapService.setApplicationVolume(200, List.of(200, 101), 0.6f);
        assertFalse(tapService.isMuted(101));
        when(wrapper.translatePidToProcessObject(102)).thenReturn(2);
        tapService.restoreApplicationMute(100, List.of(100, 102));
        assertTrue(tapService.isMuted(102));
    }

    @Test
    void restartingSameApplicationWithSamePidDoesNotRetainMuteIntent() {
        when(wrapper.translatePidToProcessObject(100)).thenReturn(1);
        var app = new OsxProcessHelper.RunningApp(100, "Safari", "/Applications/Safari.app/Contents/MacOS/Safari",
                "Foreground", "com.apple.Safari", Set.of(100));
        sndCtrl.muteApp(app, MuteType.mute);
        assertTrue(tapService.isMuted(100));
        identities.when(() -> ProcessIdentity.find(100)).thenReturn(new ProcessIdentity(100, Instant.EPOCH.plusSeconds(1)));
        sndCtrl.audioPidsFor(app);
        assertFalse(tapService.isMuted(100));
        tapService.setApplicationVolume(100, List.of(100), 0.6f);
        assertFalse(tapService.isMuted(100));
    }

    @Test
    void deadOwnerIntentAndHelperMuteAreReleasedTogether() {
        when(wrapper.translatePidToProcessObject(101)).thenReturn(1);
        tapService.muteApplication(100, List.of(100, 101), MuteType.mute);
        identities.when(() -> ProcessIdentity.find(100)).thenReturn(null);
        tapService.restoreApplicationMute(100, List.of(100, 101));
        assertFalse(tapService.isMuted(101));
        // A new application at the owner PID must not inherit intent, even with the same helper PID.
        identities.when(() -> ProcessIdentity.find(100)).thenReturn(new ProcessIdentity(100, Instant.EPOCH.plusSeconds(1)));
        when(wrapper.translatePidToProcessObject(102)).thenReturn(2);
        tapService.restoreApplicationMute(100, List.of(100, 102));
        assertFalse(tapService.isMuted(102));
    }

    @Test
    void reusedPidDisposesOldTapAndDoesNotInheritItsGainOrMute() {
        var tap = mock(ProcessTap.class);
        when(tap.muted()).thenReturn(true);
        when(tap.gain()).thenReturn(0.3f);
        when(wrapper.translatePidToProcessObject(101)).thenReturn(1);
        tapFactory.when(() -> ProcessTap.create(1, "output", 0.3f)).thenReturn(tap);
        tapService.setVolume(101, 0.3f);
        assertTrue(tapService.isMuted(101));
        identities.when(() -> ProcessIdentity.find(101)).thenReturn(new ProcessIdentity(101, Instant.EPOCH.plusSeconds(1)));
        tapService.setVolume(101, 0.6f);
        verify(tap).close();
        assertFalse(tapService.isMuted(101));
        tapFactory.verify(() -> ProcessTap.create(1, "output", 0.6f));
    }

    @Test
    void unknownProcessLifetimeCannotRetainOrCreateMuteState() {
        when(wrapper.translatePidToProcessObject(100)).thenReturn(1);
        tapService.muteApplication(100, List.of(100), MuteType.mute);
        identities.when(() -> ProcessIdentity.find(100)).thenReturn(null);
        assertFalse(tapService.isMuted(100));
        tapService.muteApplication(100, List.of(100), MuteType.mute);
        tapService.setVolume(100, 0.5f);
        assertFalse(tapService.isMuted(100));
    }

    @Test
    void successiveFocusedCommandsResolveEachApplicationsOwnCoalition() {
        var first = new OsxProcessHelper.RunningApp(100, "PWA", "/System/Library/CoreServices/Web App.app/Contents/MacOS/Web App",
                "Foreground", "pwa.first", Set.of(100, 101));
        var second = new OsxProcessHelper.RunningApp(200, "PWA", first.executablePath(), "Foreground", "pwa.second", Set.of(200, 201));
        when(processHelper.listApps()).thenReturn(List.of(first, second));
        when(wrapper.translatePidToProcessObject(101)).thenReturn(1);
        when(wrapper.translatePidToProcessObject(201)).thenReturn(2);
        when(processHelper.getFrontmostApp()).thenReturn(
                new OsxProcessHelper.FrontmostApp(100, first.name(), first.executablePath(), first.bundleId()),
                new OsxProcessHelper.FrontmostApp(200, second.name(), second.executablePath(), second.bundleId()));
        sndCtrl.setFocusVolume(0.3f);
        sndCtrl.muteFocus(MuteType.mute);
        verify(processHelper, times(2)).getFrontmostApp();
        verify(tapService).setApplicationVolume(100, List.of(100, 101), 0.3f);
        assertFalse(tapService.isMuted(101));
        assertTrue(tapService.isMuted(201));
    }

    @Test
    void identityChangeDuringTapCreationClosesUnownedTap() {
        when(wrapper.translatePidToProcessObject(100)).thenReturn(1);
        var tap = mock(ProcessTap.class);
        tapFactory.when(() -> ProcessTap.create(1, "output", 0.5f)).thenAnswer(call -> {
            identities.when(() -> ProcessIdentity.find(100)).thenReturn(new ProcessIdentity(100, Instant.EPOCH.plusSeconds(1)));
            return tap;
        });
        tapService.setVolume(100, 0.5f);
        verify(tap).close();
        assertFalse(tapService.isMuted(100));
    }

    @Test
    void ownerTerminationReleasesSurvivingHelperMute() {
        when(wrapper.translatePidToProcessObject(101)).thenReturn(1);
        tapService.muteApplication(100, List.of(100, 101), MuteType.mute);
        assertTrue(tapService.isMuted(101));
        identities.when(() -> ProcessIdentity.find(100)).thenReturn(null);
        assertFalse(tapService.isMuted(101), "a live helper must not keep its expired owner's mute");
    }

    @Test
    void restartingOwnerDoesNotCarryHelperMuteIntoVolumeCommand() {
        when(wrapper.translatePidToProcessObject(101)).thenReturn(1);
        tapService.muteApplication(100, List.of(100, 101), MuteType.mute);
        identities.when(() -> ProcessIdentity.find(100)).thenReturn(new ProcessIdentity(100, Instant.EPOCH.plusSeconds(1)));
        tapService.setApplicationVolume(100, List.of(100, 101), 0.5f);
        assertFalse(tapService.isMuted(101));
    }

    @Test
    void firstToggleUnmutesAudioHelperWhenMainProcessHasNoAudioTap() {
        when(wrapper.translatePidToProcessObject(101)).thenReturn(1);
        tapService.setMuted(101, true);
        tapService.muteApplication(100, List.of(100, 101), MuteType.toggle);
        assertFalse(tapService.isMuted(101), "the untapped GUI PID is not an unmuted audio stream");
    }

    @Test
    void ownerExitNotificationClosesMuteOnlyTapWithoutAnotherAudioCommand() {
        when(wrapper.translatePidToProcessObject(101)).thenReturn(1);
        tapService.muteApplication(100, List.of(100, 101), MuteType.mute);
        var helperTap = createdTaps.getLast();
        identities.when(() -> ProcessIdentity.find(100)).thenReturn(null);
        exits.get(new ProcessIdentity(100, Instant.EPOCH)).run();
        // Verify native release before any service query could perform lazy cleanup.
        verify(helperTap).close();
        assertFalse(tapService.isMuted(101));
    }

    @Test
    void ownerExitUnmutesButPreservesGainOnAGenuinelySurvivingAudioProcess() {
        when(wrapper.translatePidToProcessObject(101)).thenReturn(1);
        tapService.setApplicationVolume(100, List.of(100, 101), 0.3f);
        tapService.muteApplication(100, List.of(100, 101), MuteType.mute);
        var helperTap = createdTaps.getLast();
        identities.when(() -> ProcessIdentity.find(100)).thenReturn(null);
        exits.get(new ProcessIdentity(100, Instant.EPOCH)).run();
        assertFalse(helperTap.muted());
        assertEquals(0.3f, helperTap.gain());
        verify(helperTap, never()).close();
        // Reopening must not restore the old mute, but may continue controlling the same live helper.
        identities.when(() -> ProcessIdentity.find(100)).thenReturn(new ProcessIdentity(100, Instant.EPOCH.plusSeconds(1)));
        tapService.restoreApplicationMute(100, List.of(100, 101));
        assertFalse(helperTap.muted());
        tapService.muteApplication(100, List.of(100, 101), MuteType.toggle);
        assertTrue(helperTap.muted());
        tapService.muteApplication(100, List.of(100, 101), MuteType.toggle);
        assertFalse(helperTap.muted());
        assertEquals(0.3f, helperTap.gain());
    }

    @Test
    void firstToggleAfterRestartReleasesStaleHelperMuteBeforeLateExitNotification() {
        when(wrapper.translatePidToProcessObject(101)).thenReturn(1);
        tapService.muteApplication(100, List.of(100, 101), MuteType.mute);
        identities.when(() -> ProcessIdentity.find(100)).thenReturn(null);
        // New GUI process and the surviving helper; no exit callback or earlier query has run.
        tapService.muteApplication(200, List.of(200, 101), MuteType.toggle);
        assertFalse(tapService.isMuted(101));
        exits.get(new ProcessIdentity(100, Instant.EPOCH)).run();
        assertFalse(tapService.isMuted(101));
    }

    @Test
    void firstToggleAfterRestartUnmutesStaleHelperEvenIfNewMainHasAudioPresence() {
        when(wrapper.translatePidToProcessObject(101)).thenReturn(1);
        when(wrapper.translatePidToProcessObject(200)).thenReturn(2);
        tapService.muteApplication(100, List.of(100, 101), MuteType.mute);
        identities.when(() -> ProcessIdentity.find(100)).thenReturn(null);
        tapService.muteApplication(200, List.of(200, 101), MuteType.toggle);
        assertFalse(tapService.isMuted(101));
        assertFalse(tapService.isMuted(200));
    }

    @Test
    void oldOwnerExitCannotUnmuteAReopenedOwnersTapAtTheSamePid() {
        when(wrapper.translatePidToProcessObject(101)).thenReturn(1);
        var oldOwner = new ProcessIdentity(100, Instant.EPOCH);
        tapService.setApplicationVolume(100, List.of(100, 101), 0.3f);
        tapService.muteApplication(100, List.of(100, 101), MuteType.mute);
        var oldExit = exits.get(oldOwner);
        identities.when(() -> ProcessIdentity.find(100)).thenReturn(new ProcessIdentity(100, Instant.EPOCH.plusSeconds(1)));
        tapService.setApplicationVolume(100, List.of(100, 101), 0.5f);
        tapService.muteApplication(100, List.of(100, 101), MuteType.mute);
        var helperTap = createdTaps.getLast();
        oldExit.run();
        assertTrue(helperTap.muted());
        assertEquals(0.5f, helperTap.gain());
        verify(helperTap, never()).close();
    }

    @Test
    void terminatingOnePwaDoesNotReleaseAnotherPwasMute() {
        when(wrapper.translatePidToProcessObject(101)).thenReturn(1);
        when(wrapper.translatePidToProcessObject(201)).thenReturn(2);
        tapService.muteApplication(100, List.of(100, 101), MuteType.mute);
        tapService.muteApplication(200, List.of(200, 201), MuteType.mute);
        var otherTap = createdTaps.getLast();
        identities.when(() -> ProcessIdentity.find(100)).thenReturn(null);
        exits.get(new ProcessIdentity(100, Instant.EPOCH)).run();
        assertFalse(tapService.isMuted(101));
        assertTrue(otherTap.muted());
        verify(otherTap, never()).close();
    }

    @Test
    void helperReplacementDoesNotRegisterAnotherOwnerExitNotification() {
        when(wrapper.translatePidToProcessObject(101)).thenReturn(1);
        when(wrapper.translatePidToProcessObject(102)).thenReturn(2);
        tapService.muteApplication(100, List.of(100, 101), MuteType.mute);
        identities.when(() -> ProcessIdentity.find(101)).thenReturn(null);
        tapService.restoreApplicationMute(100, List.of(100, 102));
        assertTrue(tapService.isMuted(102));
        identities.verify(() -> ProcessIdentity.onExit(eq(new ProcessIdentity(100, Instant.EPOCH)), any(Runnable.class)), times(1));
    }

    @Test
    void noTapAtFullVolumeDoesNotRegisterAnExitNotification() {
        when(wrapper.translatePidToProcessObject(101)).thenReturn(1);
        tapService.setApplicationVolume(100, List.of(100, 101), 1f);
        identities.verify(() -> ProcessIdentity.onExit(any(ProcessIdentity.class), any(Runnable.class)), never());
    }

    @Test
    void ownerChangeDuringHelperCreationDisposesTap() {
        when(wrapper.translatePidToProcessObject(101)).thenReturn(1);
        var tap = mock(ProcessTap.class);
        tapFactory.when(() -> ProcessTap.create(1, "output", 0.5f)).thenAnswer(call -> {
            identities.when(() -> ProcessIdentity.find(100)).thenReturn(new ProcessIdentity(100, Instant.EPOCH.plusSeconds(1)));
            return tap;
        });
        tapService.setApplicationVolume(100, List.of(100, 101), 0.5f);
        verify(tap).close();
        assertFalse(tapService.isMuted(101));
    }

}
