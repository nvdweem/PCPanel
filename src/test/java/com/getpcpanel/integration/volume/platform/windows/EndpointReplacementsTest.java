package com.getpcpanel.integration.volume.platform.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.volume.platform.DataFlow;

class EndpointReplacementsTest {
    private static final String OLD = "{0.0.0.00000000}.{23f7633c-2cd8-44ee-9518-6fa31ed81f28}";
    private static final String NEW = "{0.0.0.00000000}.{11111111-2222-3333-4444-555555555555}";
    private static final String OTHER = "{0.0.0.00000000}.{aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee}";
    private static final String MIC = "{0.0.1.00000000}.{99999999-8888-7777-6666-555555555555}";

    private static KnownEndpoint out(String name, String iface, long firstSeen, long lastSeen) {
        return new KnownEndpoint(name, iface, DataFlow.dfRender, firstSeen, lastSeen);
    }

    @Test
    void hardwareNameAndLabelComeFromTheInterfaceName() {
        var endpoint = out("LSX II (High Definition Audio Device)", "High Definition Audio Device", 0, 0);
        assertEquals("High Definition Audio Device", endpoint.hardwareName());
        assertEquals("LSX II", endpoint.label());
    }

    @Test
    void withoutAnInterfaceNameTheTrailingParenthesesAreTheHardware() {
        var endpoint = out("Speakers (2- Schiit Modi 3+ (USB))", null, 0, 0);
        assertEquals("Schiit Modi 3+ (USB)", endpoint.hardwareName(), "the instance number and nesting are handled");
        assertEquals("Speakers", endpoint.label());
        assertNull(out("Plain name", null, 0, 0).hardwareName());
    }

    @Test
    void theInstanceNumberDoesNotMakeItOtherHardware() {
        var lost = out("Speakers (USB Audio)", "USB Audio", 1, 10);
        var present = Map.of(NEW, out("Speakers (2- USB Audio)", "2- USB Audio", 20, 30));
        assertEquals(Optional.of(NEW), EndpointReplacements.replacement(lost, present));
    }

    @Test
    void aRenameLostWithTheOldEndpointStillMatchesOnTheHardware() {
        // #194: "LSX II" was a rename; the endpoint Windows built anew is plain "Speakers" again.
        var lost = out("LSX II (High Definition Audio Device)", "High Definition Audio Device", 1, 10);
        var present = Map.of(
                NEW, out("Speakers (High Definition Audio Device)", "High Definition Audio Device", 20, 30),
                OTHER, out("Speakers (Schiit Modi 3+)", "Schiit Modi 3+", 1, 30));
        assertEquals(Optional.of(NEW), EndpointReplacements.replacement(lost, present));
    }

    @Test
    void theLabelPicksBetweenEndpointsOfOneDevice() {
        var lost = out("Digital Output (Realtek Audio)", "Realtek Audio", 1, 10);
        var present = Map.of(
                NEW, out("Digital Output (Realtek Audio)", "Realtek Audio", 20, 30),
                OTHER, out("Speakers (Realtek Audio)", "Realtek Audio", 20, 30));
        assertEquals(Optional.of(NEW), EndpointReplacements.replacement(lost, present));
    }

    @Test
    void equalCandidatesAreNeverGuessedBetween() {
        var lost = out("Headset (USB Headset)", "USB Headset", 1, 10);
        var present = Map.of(
                NEW, out("Speakers (USB Headset)", "USB Headset", 20, 30),
                OTHER, out("Speakers (USB Headset)", "USB Headset", 20, 30));
        assertEquals(Optional.empty(), EndpointReplacements.replacement(lost, present));
    }

    @Test
    void anIdenticalDeviceUsedAlongsideIsNotAReplacement() {
        // Two identical headsets were present together (seen at 10); unplugging one leaves its actions alone.
        var lost = out("Headset (USB Headset)", "USB Headset", 1, 10);
        var present = Map.of(NEW, out("Headset (USB Headset)", "USB Headset", 10, 30));
        assertEquals(Optional.empty(), EndpointReplacements.replacement(lost, present));
    }

    @Test
    void anEndpointNeverSeenPresentHasNoTimeToCompare() {
        var lost = out("Speakers (Schiit Modi 3+)", "Schiit Modi 3+", 0, 0);
        var present = Map.of(NEW, out("Speakers (Schiit Modi 3+)", "Schiit Modi 3+", 5, 30));
        assertEquals(Optional.of(NEW), EndpointReplacements.replacement(lost, present));
    }

    @Test
    void playbackIsNeverReplacedByRecording() {
        var lost = out("Speakers (Yeti)", "Yeti", 1, 10);
        var present = Map.of(MIC, new KnownEndpoint("Microphone (Yeti)", "Yeti", DataFlow.dfCapture, 20, 30));
        assertEquals(Optional.empty(), EndpointReplacements.replacement(lost, present));
    }

    @Test
    void withoutAHardwareNameOnlyTheSameNameMatches() {
        var lost = out("Line", null, 1, 10);
        assertEquals(Optional.of(NEW), EndpointReplacements.replacement(lost, Map.of(NEW, out("line", null, 20, 30))));
        assertEquals(Optional.empty(), EndpointReplacements.replacement(lost, Map.of(NEW, out("Line 2", null, 20, 30))));
    }

    @Test
    void onlyMissingReferencedIdsArePlanned() {
        var known = Map.of(
                OLD, out("Speakers (Schiit Modi 3+)", "Schiit Modi 3+", 1, 10),
                NEW, out("Speakers (Schiit Modi 3+)", "Schiit Modi 3+", 20, 30),
                OTHER, out("Speakers (Realtek Audio)", "Realtek Audio", 1, 30));
        assertEquals(Map.of(OLD, NEW), EndpointReplacements.plan(Set.of(OLD, OTHER), known, Set.of(NEW, OTHER)));
        assertEquals(Map.of(), EndpointReplacements.plan(Set.of(OTHER), known, Set.of(NEW, OTHER)));
    }

    @Test
    void endpointIdsAreFoundAnywhereInTheText() {
        var text = "{\"deviceId\":\"" + OLD + "\",\"devices\":[\"" + MIC + "\"],\"device\":\"default\",\"t\":\"{{ audio.device['" + NEW + "'] }}\"}";
        assertEquals(Set.of(OLD, MIC, NEW), EndpointReplacements.referencedIds(text));
    }

    @Test
    void aDescriptionFromTheDllIsRead() {
        assertEquals(Optional.of(new KnownEndpoint("Speakers (Modi)", "Modi", DataFlow.dfCapture, 0, 0)),
                SndCtrlWindows.parseDescription("Speakers (Modi)\nModi\n1"));
        assertEquals(Optional.of(new KnownEndpoint("Speakers", null, DataFlow.dfRender, 0, 0)), SndCtrlWindows.parseDescription("Speakers\n\n0"));
        assertEquals(Optional.empty(), SndCtrlWindows.parseDescription(""));
        assertEquals(Optional.empty(), SndCtrlWindows.parseDescription(null));
        assertEquals(Optional.empty(), SndCtrlWindows.parseDescription("a\nb\nx"));
    }
}
