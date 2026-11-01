package com.getpcpanel.integration.volume.platform.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.volume.platform.AudioReadyEvent;
import com.getpcpanel.integration.volume.platform.DataFlow;

/**
 * The DLL lists the audio devices and their sessions on a thread of its own, after the bean is up, and says when it
 * takes calls. That is what the dial levels sent before it wait for.
 */
class SndCtrlWindowsAudioReadyTest {
    @Test
    void theDllSayingItIsReadyIsPublished() {
        var snd = new RecordingSndCtrl();

        snd.audioReady();

        assertEquals(List.of(new AudioReadyEvent()), snd.events);
    }

    @Test
    void listingDevicesIsNotYetReady() {
        var snd = new RecordingSndCtrl();
        snd.deviceAdded("Speakers", "speakers", 1f, false, DataFlow.dfRender.ordinal());
        snd.setDefaultDevice("speakers", DataFlow.dfRender.ordinal(), 0);

        assertEquals(0, snd.events.stream().filter(AudioReadyEvent.class::isInstance).count());
    }

    private static final class RecordingSndCtrl extends SndCtrlWindows {
        private final List<Object> events = new ArrayList<>();

        @Override
        void fireEvent(Object event) {
            events.add(event);
        }

        @Override
        boolean hasAudioPolicyConfigFactory() {
            return true;
        }
    }
}
