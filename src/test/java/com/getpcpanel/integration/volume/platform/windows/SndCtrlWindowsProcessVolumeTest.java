package com.getpcpanel.integration.volume.platform.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.volume.platform.DataFlow;

/**
 * Which sessions an App-volume action reaches. No device (the default) reaches the app on every output, as on Linux
 * and macOS and as App mute does — Wave Link, for one, plays a browser through its own Browsers device rather than
 * the default one.
 */
class SndCtrlWindowsProcessVolumeTest {
    private static final String GAME_ID = "game-default";
    private static final String BROWSERS_ID = "browsers";

    @Test
    void noDeviceMeansEveryOutput() {
        var snd = withEdgeOnTwoDevices();

        snd.setProcessVolume("msedge.exe", "", 0.4f);

        assertEquals(List.of(GAME_ID + ":1", BROWSERS_ID + ":2"), snd.set);
    }

    @Test
    void aChosenDeviceStaysScoped() {
        var snd = withEdgeOnTwoDevices();

        snd.setProcessVolume("msedge.exe", BROWSERS_ID, 0.4f);

        assertEquals(List.of(BROWSERS_ID + ":2"), snd.set);
    }

    private static RecordingSndCtrl withEdgeOnTwoDevices() {
        var snd = new RecordingSndCtrl();
        var game = (WindowsAudioDevice) snd.deviceAdded("Game (Elgato Virtual Audio)", GAME_ID, 1f, false, DataFlow.dfRender.ordinal());
        var browsers = (WindowsAudioDevice) snd.deviceAdded("Browsers (Elgato Virtual Audio)", BROWSERS_ID, 1f, false, DataFlow.dfRender.ordinal());
        snd.setDefaultDevice(GAME_ID, DataFlow.dfRender.ordinal(), 1);
        game.addSession(11, 1, "msedge.exe", "Edge", null, 1f, false);
        browsers.addSession(12, 2, "msedge.exe", "Edge", null, 0.8f, false);
        browsers.addSession(13, 3, "Spotify.exe", "Spotify", null, 0.8f, false);
        return snd;
    }

    /** Records the sessions the real class would hand to the DLL, so no native library is needed. */
    private static final class RecordingSndCtrl extends SndCtrlWindows {
        private final List<String> set = new ArrayList<>();

        @Override
        public void setProcessVolume(WindowsAudioSession session, float volume) {
            set.add(session.device().id() + ":" + session.pid());
        }
    }
}
