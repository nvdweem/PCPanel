package com.getpcpanel.integration.volume.platform.linux;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.getpcpanel.integration.volume.platform.linux.PulseAudioEventListener.LinuxDeviceChangedEvent;
import com.getpcpanel.integration.volume.platform.linux.PulseAudioEventListener.LinuxSessionChangedEvent;

/** The {@code pactl subscribe} lines are as PipeWire 1.0.5 and PulseAudio 17 print them. */
class PulseAudioEventListenerTest {
    @CsvSource(delimiter = ';', value = {
            "Event 'change' on server #4294967295; device",   // the default sink or source changed
            "Event 'new' on source #148; device",             // a microphone was plugged in
            "Event 'remove' on source #148; device",
            "Event 'new' on sink #47; device",
            "Event 'remove' on sink #47; device",
            "Event 'change' on sink-input #12; session",
            "Event 'new' on source-output #90; none",         // an app started recording: no device changed
            "Event 'remove' on source-output #90; none",
            "Event 'new' on client #142; none",
            "Event 'change' on source #61; none",             // a volume or mute change on a source
    })
    @ParameterizedTest
    void refreshesWhatTheEventChanged(String line, String expected) {
        var fired = new ArrayList<Object>();
        var sut = new PulseAudioEventListener();
        sut.eventBus = new SndCtrlPulseAudioTest.RecordingEventBus(fired);

        sut.checkTrigger(line);

        assertEquals(expected, kinds(fired));
    }

    private static String kinds(List<Object> fired) {
        if (fired.isEmpty()) {
            return "none";
        }
        return String.join(",", fired.stream().map(e -> switch (e) {
            case LinuxDeviceChangedEvent ignored -> "device";
            case LinuxSessionChangedEvent ignored -> "session";
            default -> e.getClass().getSimpleName();
        }).toList());
    }
}
