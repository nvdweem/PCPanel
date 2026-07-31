package com.getpcpanel.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getpcpanel.report.dto.BugReportRequest;

@DisplayName("BugReportUrlBuilder (the prefilled issue link stays followable)")
class BugReportUrlBuilderTest {
    private static BugReportRequest request(String summary, String steps, String expected) {
        return new BugReportRequest(summary, expected, steps, null, true, true, false, false, List.of(), List.of());
    }

    private static String bodyOf(String url) {
        return url.substring(url.indexOf("&body=") + "&body=".length());
    }

    @Test
    @DisplayName("a normal report is carried in full")
    void carriesAShortReportInFull() {
        var url = new BugReportUrlBuilder().build(
                request("Volume jumps to 100%", "Turn knob 2", "It stays where I set it"),
                "2.0.83", "windows", "PCPanel Pro (0123)", "pcpanel-report-20260730-141200.zip");

        assertTrue(url.startsWith("https://github.com/nvdweem/PCPanel/issues/new?labels=bug"), url);
        var body = bodyOf(url);
        assertTrue(body.contains("Turn+knob+2"), "the steps should survive");
        assertTrue(body.contains("PCPanel+Pro"), "the device should be named");
        assertTrue(body.contains("pcpanel-report-20260730-141200.zip"), "the bundle to attach should be named");
    }

    @Test
    @DisplayName("a report far past the limit is shortened rather than producing an unfollowable link")
    void boundsAnOversizedReport() {
        var huge = "x".repeat(50_000);

        var url = new BugReportUrlBuilder().build(
                request(huge, huge, huge), "2.0.83", "linux", "", "bundle.zip");

        assertTrue(bodyOf(url).length() <= BugReportUrlBuilder.MAX_BODY,
                () -> "the encoded body must stay within the cap, was " + bodyOf(url).length());
    }

    @Test
    @DisplayName("the identifying block survives shortening — it is what makes the report triageable")
    void keepsTheContextBlockWhenShortening() {
        var huge = "x".repeat(50_000);

        var body = bodyOf(new BugReportUrlBuilder().build(
                request(huge, huge, huge), "2.0.83", "linux", "", "bundle.zip"));

        assertTrue(body.contains("2.0.83"), "the version must survive");
        assertTrue(body.contains("linux"), "the OS must survive");
        assertTrue(body.contains("bundle.zip"), "the bundle name must survive");
    }

    @Test
    @DisplayName("the title is the summary, bounded")
    void derivesABoundedTitle() {
        assertEquals("Bug report", BugReportUrlBuilder.title(request("   ", "", "")));

        var title = BugReportUrlBuilder.title(request("y".repeat(500), "", ""));
        assertTrue(title.length() <= 80, () -> "title was " + title.length() + " chars");
    }
}
