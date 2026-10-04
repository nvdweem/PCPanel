package com.getpcpanel.alerts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.profile.dto.NotificationAlert;
import com.getpcpanel.profile.dto.NotificationAlert.AlertEffect;
import com.getpcpanel.profile.dto.NotificationAlert.AlertTrigger;

class AlertStateTest {
    private static final NotificationAlert DISCORD = new NotificationAlert(AlertTrigger.TASKBAR_FLASH, "Discord.exe", "knob:1", "#8000FF", false, null);
    private static final NotificationAlert ON_AIR = new NotificationAlert(AlertTrigger.MIC_IN_USE, "", "logo", "#FF0000", false, null);

    @Test
    void flashLightsUntilTheAppIsFocused() {
        var sut = new AlertState();
        sut.configure(List.of(DISCORD));
        sut.onFlash("discord.exe", 0);
        assertEquals(Map.of("knob:1", "#8000FF"), sut.lit(10));

        sut.onFocus("C:\\Users\\me\\AppData\\Local\\Discord\\app-1.0\\Discord.exe");
        assertTrue(sut.lit(20).isEmpty());
    }

    @Test
    void otherAppsDoNotTrigger() {
        var sut = new AlertState();
        sut.configure(List.of(DISCORD));
        sut.onFlash("Teams.exe", 0);
        assertTrue(sut.lit(10).isEmpty());
    }

    @Test
    void stopsAfterItsTimeout() {
        var sut = new AlertState();
        sut.configure(List.of(new NotificationAlert(AlertTrigger.TASKBAR_FLASH, "Discord", "knob:1", "#8000FF", false, 5)));
        sut.onFlash("Discord.exe", 0);
        assertEquals(1, sut.lit(4_999).size());
        assertTrue(sut.lit(5_000).isEmpty());
    }

    @Test
    void blinkAlternates() {
        var sut = new AlertState();
        sut.configure(List.of(new NotificationAlert(AlertTrigger.TASKBAR_FLASH, "Discord", "knob:1", "#8000FF", true, null)));
        sut.onFlash("Discord.exe", 0);
        assertEquals(1, sut.lit(0).size());
        assertTrue(sut.lit(500).isEmpty());
        assertEquals(1, sut.lit(1_000).size());
    }

    @Test
    void intensityFollowsTheEffect() {
        assertEquals(1.0, AlertState.intensity(AlertEffect.STEADY, 600, 1_200));
        assertEquals(1.0, AlertState.intensity(AlertEffect.BLINK, 0, 1_200));
        assertEquals(0.0, AlertState.intensity(AlertEffect.BLINK, 600, 1_200));
        assertEquals(0.12, AlertState.intensity(AlertEffect.PULSE, 0, 1_200), 1e-9);
        assertEquals(1.0, AlertState.intensity(AlertEffect.PULSE, 600, 1_200), 1e-9);
    }

    @Test
    void pulseScalesTheColour() {
        var sut = new AlertState();
        sut.configure(List.of(new NotificationAlert(AlertTrigger.MIC_IN_USE, "", "logo", "#ff0000", false, null, false, null,
                AlertEffect.PULSE, null, null, null)));
        sut.onMicUsers(Set.of("zoom.exe"), 0);
        assertEquals(Map.of("logo", "#1f0000"), sut.lit(0));
    }

    @Test
    void legacyBlinkReadsAsTheBlinkEffect() throws Exception {
        var alert = new ObjectMapper().readValue("""
                {"trigger":"TASKBAR_FLASH","app":"Discord.exe","target":"knob:1","color":"#8000FF","blink":true}""", NotificationAlert.class);
        assertEquals(AlertEffect.BLINK, alert.effectOrDefault());
        assertEquals(1_000, alert.periodOrDefault(), "an older save keeps its half-second blink");
        assertEquals(AlertEffect.STEADY, DISCORD.effectOrDefault());
        assertEquals(1_200, new NotificationAlert(AlertTrigger.MIC_IN_USE, "", "logo", "#ff0000", true, null, false, null,
                AlertEffect.BLINK, null, null, null).periodOrDefault());
    }

    @Test
    void redrawsAtTheNextBlinkEdge() {
        var sut = new AlertState();
        sut.configure(List.of(new NotificationAlert(AlertTrigger.MIC_IN_USE, "", "logo", "#ff0000", false, null, false, null,
                AlertEffect.BLINK, 300, null, null)));
        sut.onMicUsers(Set.of("zoom.exe"), 0);
        assertEquals(AlertState.TICK_MS, sut.frame(0).nextTickMs(), "the edge at 150 is further off than a tick");
        assertEquals(50, sut.frame(100).nextTickMs());
        assertEquals(100, sut.frame(200).nextTickMs());
        sut.onMicUsers(Set.of(), 300);
        assertEquals(AlertState.TICK_MS, sut.frame(300).nextTickMs());
    }

    @Test
    void pulseRedrawsOften() {
        var sut = new AlertState();
        sut.configure(List.of(withPeriod(1_200)));
        sut.onMicUsers(Set.of("zoom.exe"), 0);
        var frame = sut.frame(0);
        assertTrue(frame.pulsing());
        assertEquals(AlertState.PULSE_TICK_MS, frame.nextTickMs());
        assertEquals(Set.of(0), frame.indexes());
        assertEquals(Map.of("logo", "#1f0000"), frame.colors());
    }

    @Test
    void editingAnAlertKeepsItLit() {
        var sut = new AlertState();
        sut.configure(List.of(DISCORD));
        sut.onFlash("Discord.exe", 0);
        sut.configure(List.of(new NotificationAlert(AlertTrigger.TASKBAR_FLASH, "Discord.exe", "knob:1", "#00FF00", false, null)));
        assertEquals(Map.of("knob:1", "#00FF00"), sut.lit(10));
    }

    @Test
    void anAlertMovedInTheListKeepsItsState() {
        var sut = new AlertState();
        sut.configure(List.of(DISCORD));
        sut.onFlash("Discord.exe", 0);
        sut.configure(List.of(ON_AIR, DISCORD));
        assertEquals(Set.of(1), sut.litIndexes(10));
    }

    @Test
    void anotherAppInTheSameSlotStartsDark() {
        var sut = new AlertState();
        sut.configure(List.of(DISCORD));
        sut.onFlash("Discord.exe", 0);
        sut.configure(List.of(new NotificationAlert(AlertTrigger.TASKBAR_FLASH, "Teams.exe", "knob:1", "#8000FF", false, null)));
        assertTrue(sut.lit(10).isEmpty());
    }

    @Test
    void switchingAnAlertOffDarkensIt() {
        var sut = new AlertState();
        sut.configure(List.of(DISCORD));
        sut.onFlash("Discord.exe", 0);
        sut.configure(List.of(new NotificationAlert(AlertTrigger.TASKBAR_FLASH, "Discord.exe", "knob:1", "#8000FF", false, null, true, null)));
        assertTrue(sut.lit(10).isEmpty());
    }

    @Test
    void periodIsClamped() {
        assertEquals(200, withPeriod(10).periodOrDefault());
        assertEquals(10_000, withPeriod(60_000).periodOrDefault());
        assertEquals(800, withPeriod(800).periodOrDefault());
    }

    @Test
    void previewLightsUntilItEnds() {
        var sut = new AlertState();
        sut.configure(List.of(DISCORD));
        sut.preview(0, 5_000);
        assertEquals(Map.of("knob:1", "#8000FF"), sut.lit(1_000));
        assertEquals(Set.of(0), sut.litIndexes(1_000));
        assertTrue(sut.lit(6_000).isEmpty());
        assertTrue(sut.litIndexes(6_000).isEmpty());
    }

    @Test
    void litIndexesCountSwitchedOffAlerts() {
        var off = new NotificationAlert(AlertTrigger.MIC_IN_USE, "", "knob:2", "#00FF00", false, null, true, null);
        var sut = new AlertState();
        sut.configure(List.of(off, ON_AIR));
        sut.onMicUsers(Set.of("zoom.exe"), 0);
        assertEquals(Set.of(1), sut.litIndexes(0));
    }

    @Test
    void litIndexesHoldThroughTheBlinkOffHalf() {
        var sut = new AlertState();
        sut.configure(List.of(new NotificationAlert(AlertTrigger.TASKBAR_FLASH, "Discord", "knob:1", "#8000FF", true, null)));
        sut.onFlash("Discord.exe", 0);
        assertTrue(sut.lit(600).isEmpty());
        assertEquals(Set.of(0), sut.litIndexes(600));
    }

    private static NotificationAlert withPeriod(int periodMs) {
        return new NotificationAlert(AlertTrigger.MIC_IN_USE, "", "logo", "#ff0000", false, null, false, null, AlertEffect.PULSE, periodMs, null, null);
    }

    @Test
    void micWithoutAppMeansAnyApp() {
        var sut = new AlertState();
        sut.configure(List.of(ON_AIR));
        sut.onMicUsers(Set.of(), 0);
        assertTrue(sut.lit(0).isEmpty());
        sut.onMicUsers(Set.of("zoom.exe"), 0);
        assertEquals(Map.of("logo", "#FF0000"), sut.lit(0));
    }

    @Test
    void disabledAlertsNeverLight() {
        var sut = new AlertState();
        sut.configure(List.of(new NotificationAlert(AlertTrigger.MIC_IN_USE, "", "logo", "#FF0000", false, null, true, null)));
        sut.onMicUsers(Set.of("zoom.exe"), 0);
        assertTrue(sut.lit(0).isEmpty());
        assertFalse(sut.hasTrigger(AlertTrigger.MIC_IN_USE));
    }

    @Test
    void aSwitchedOffAlertKeepsTheOthersLit() {
        var off = new NotificationAlert(AlertTrigger.MIC_IN_USE, "", "knob:2", "#00FF00", false, null, true, null);
        var alerts = List.of(DISCORD, off, ON_AIR);
        var sut = new AlertState();
        sut.configure(alerts);
        sut.onFlash("Discord.exe", 0);
        sut.onMicUsers(Set.of("zoom.exe"), 0);

        sut.configure(List.copyOf(alerts)); // the service re-reads the same list every tick
        assertEquals(Map.of("knob:1", "#8000FF", "logo", "#FF0000"), sut.lit(10));
    }

    @Test
    void anyAppSkipsTheExceptions() {
        var sut = new AlertState();
        sut.configure(List.of(new NotificationAlert(AlertTrigger.MIC_IN_USE, "", "logo", "#FF0000", false, null, false, List.of("WaveLink.exe"))));
        sut.onMicUsers(Set.of("elgato.wavelink_g54w8ztgkx496"), 0);
        assertTrue(sut.lit(0).isEmpty());
        sut.onMicUsers(Set.of("elgato.wavelink_g54w8ztgkx496", "zoom.exe"), 0);
        assertEquals(Map.of("logo", "#FF0000"), sut.lit(0));
    }

    @Test
    void storeAppIdMatchesItsExe() {
        assertTrue(AlertState.sameMicApp("WaveLink.exe", "elgato.wavelink_g54w8ztgkx496"));
        assertTrue(AlertState.sameMicApp("elgato.wavelink_g54w8ztgkx496", "elgato.wavelink_g54w8ztgkx496"));
        assertFalse(AlertState.sameMicApp("Discord.exe", "elgato.wavelink_g54w8ztgkx496"));
    }

    private static NotificationAlert notification(String app, String source, Integer stopAfterSeconds) {
        return new NotificationAlert(AlertTrigger.NOTIFICATION, app, "knob:3", "#00AAFF", false, stopAfterSeconds, false, null, null, null, null, source);
    }

    @Test
    void notificationsPresentAtTheFirstLookDoNotLight() {
        var sut = new AlertState();
        sut.configure(List.of(notification("Discord.exe", null, null)));
        sut.onNotifications(Set.of("discord"), 0);
        assertTrue(sut.lit(0).isEmpty(), "the first call only records what is there");
        sut.onNotifications(Set.of("discord"), 2_000);
        assertTrue(sut.lit(2_000).isEmpty(), "still the same notification");
    }

    @Test
    void aNewNotificationLightsUntilTheAppIsFocused() {
        var sut = new AlertState();
        sut.configure(List.of(notification("Discord.exe", null, null)));
        sut.onNotifications(Set.of("discord"), 0);
        sut.onNotifications(Set.of(), 2_000);
        sut.onNotifications(Set.of("discord"), 4_000);
        assertEquals(Map.of("knob:3", "#00AAFF"), sut.lit(4_000));

        sut.onFocus("Discord.exe");
        assertTrue(sut.lit(4_100).isEmpty());
        sut.onNotifications(Set.of("discord"), 6_000);
        assertTrue(sut.lit(6_000).isEmpty(), "the notification it already showed does not light it again");
    }

    @Test
    void aHandlerIdMatchesByTheExeName() {
        var sut = new AlertState();
        sut.configure(List.of(notification("C:\\Apps\\Discord.exe", null, null)));
        sut.onNotifications(Set.of(), 0);
        sut.onNotifications(Set.of("com.squirrel.discord.discord"), 2_000);
        assertEquals(1, sut.lit(2_000).size());
    }

    @Test
    void theSourceWinsOverTheApp() {
        var sut = new AlertState();
        sut.configure(List.of(notification("Discord.exe", "Microsoft.Teams", null)));
        sut.onNotifications(Set.of(), 0);
        sut.onNotifications(Set.of("discord"), 2_000);
        assertTrue(sut.lit(2_000).isEmpty(), "with a source the app's own notifications do not count");
        sut.onNotifications(Set.of("discord", "microsoft.teams"), 4_000);
        assertEquals(Map.of("knob:3", "#00AAFF"), sut.lit(4_000));
    }

    @Test
    void aNotificationLightClearsWhenTheNotificationIsGone() {
        var sut = new AlertState();
        sut.configure(List.of(notification("Discord.exe", null, null)));
        sut.onNotifications(Set.of(), 0);
        sut.onNotifications(Set.of("discord"), 2_000);
        assertEquals(1, sut.lit(2_000).size());
        sut.onNotifications(Set.of(), 4_000);
        assertTrue(sut.lit(4_000).isEmpty());
    }

    @Test
    void aNotificationLightStopsAfterItsTimeout() {
        var sut = new AlertState();
        sut.configure(List.of(notification("Discord.exe", null, 5)));
        sut.onNotifications(Set.of(), 0);
        sut.onNotifications(Set.of("discord"), 2_000);
        assertEquals(1, sut.lit(6_999).size());
        assertTrue(sut.lit(7_000).isEmpty());
    }

    @Test
    void aNewerNotificationFromAnAppAlreadyListedLightsAgain() {
        var sut = new AlertState();
        sut.configure(List.of(notification("Discord.exe", null, null)));
        sut.onNotifications(Map.of("discord", 10L), 0);
        sut.onNotifications(Map.of("discord", 10L), 2_000);
        assertTrue(sut.lit(2_000).isEmpty());
        sut.onNotifications(Map.of("discord", 11L), 4_000);
        assertEquals(1, sut.lit(4_000).size(), "an old notification left in the list does not hide a new one");
    }

    @Test
    void notWatchingStartsOverFromAFirstLook() {
        var sut = new AlertState();
        sut.configure(List.of(notification("Discord.exe", null, null)));
        sut.onNotifications(Set.of(), 0);
        sut.forgetNotifications();
        sut.onNotifications(Set.of("discord"), 2_000);
        assertTrue(sut.lit(2_000).isEmpty(), "what was there when watching resumed does not count as new");
    }

    private static NotificationAlert windowTitle(String app, String pattern) {
        return new NotificationAlert(AlertTrigger.WINDOW_TITLE, app, "knob:0", "#0078D4", false, null, false, null, null, null, pattern, null);
    }

    @Test
    void aBlankPatternMatchesAnUnreadCount() {
        assertTrue(NotificationAlert.titleMatches(null, "Inbox (3) - Outlook"));
        assertFalse(NotificationAlert.titleMatches("", "Inbox - Outlook"));
        assertFalse(NotificationAlert.titleMatches("  ", "Year (abc) - Outlook"));
    }

    @Test
    void aPatternMatchesAnywhereIgnoringCase() {
        assertTrue(NotificationAlert.titleMatches("ringing", "Teams - Call RINGING"));
        assertFalse(NotificationAlert.titleMatches("ringing", "Teams - Chat"));
    }

    @Test
    void aWindowTitleLightsWhileItMatches() {
        var sut = new AlertState();
        sut.configure(List.of(windowTitle("Outlook.exe", null)));
        sut.onTitles(Map.of("outlook", List.of("Inbox (2)")), 0);
        assertEquals(Map.of("knob:0", "#0078D4"), sut.lit(0));

        sut.onTitles(Map.of("outlook", List.of("Inbox")), 1_000);
        assertTrue(sut.lit(1_000).isEmpty());
    }

    @Test
    void anotherAppsTitleDoesNotLight() {
        var sut = new AlertState();
        sut.configure(List.of(windowTitle("Outlook.exe", "ringing")));
        sut.onTitles(Map.of("teams", List.of("Call ringing"), "outlook", List.of("Inbox", "Calendar")), 0);
        assertTrue(sut.lit(0).isEmpty());
        sut.onTitles(Map.of("outlook", List.of("Inbox", "Reminder ringing")), 1_000);
        assertEquals(1, sut.lit(1_000).size(), "any of the app's windows counts");
    }

    @Test
    void aWindowTitleWithoutAnAppMatchesAnyApp() {
        var sut = new AlertState();
        sut.configure(List.of(windowTitle("", "ringing"), new NotificationAlert(AlertTrigger.WINDOW_TITLE, null, "knob:1", "#0078D4", false, null, false, null, null, null, "ringing", null)));
        sut.onTitles(Map.of("outlook", List.of("Inbox")), 0);
        assertTrue(sut.lit(0).isEmpty());
        sut.onTitles(Map.of("outlook", List.of("Inbox"), "teams", List.of("Call ringing")), 1_000);
        assertEquals(Set.of("knob:0", "knob:1"), sut.lit(1_000).keySet(), "blank and unset both mean any app");
    }
}
