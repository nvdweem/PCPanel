package com.getpcpanel.integration.volume.platform.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.volume.platform.DataFlow;
import com.getpcpanel.integration.volume.platform.Role;

/**
 * Covers how {@link SndCtrlWindows#setDefaultDevice(String, DataFlow, Role)} turns a configured device
 * reference into the endpoint it switches to. The reference is whatever the user's action stored: an
 * endpoint id, or a piece of a device's name — the latter being the point of the action, so a switch
 * survives Windows renaming a device (its bracketed enumeration index changes on every re-plug).
 */
class SndCtrlWindowsDefaultDeviceTest {
    private static final String SPEAKERS_ID = "{0.0.0.00000000}.{6372e3b4-dee7-4434-9d2f-c0531d9b7355}";
    private static final String HEADSET_ID = "{0.0.0.00000000}.{aaaaaaaa-dee7-4434-9d2f-c0531d9b7355}";
    private static final String YETI_ID = "{0.0.1.00000000}.{d4b7b81d-636c-4222-bbaf-43508e4c1bc1}";

    @Test
    void resolvesAnEndpointId() {
        var snd = withDevices();

        snd.setDefaultDevice(SPEAKERS_ID, DataFlow.dfRender, Role.roleMultimedia);

        assertEquals(List.of(SPEAKERS_ID), snd.switchedTo);
    }

    @Test
    void resolvesPartOfADeviceName() {
        var snd = withDevices();

        snd.setDefaultDevice("Yeti", DataFlow.dfCapture, Role.roleMultimedia);

        assertEquals(List.of(YETI_ID), snd.switchedTo);
    }

    @Test
    void matchesTheNameRegardlessOfCase() {
        var snd = withDevices();

        snd.setDefaultDevice("yeti", DataFlow.dfCapture, Role.roleMultimedia);

        assertEquals(List.of(YETI_ID), snd.switchedTo);
    }

    @Test
    void prefersAnExactNameOverALongerOneContainingIt() {
        var snd = withDevices();

        snd.setDefaultDevice("Headset", DataFlow.dfRender, Role.roleMultimedia);

        assertEquals(List.of(HEADSET_ID), snd.switchedTo);
    }

    @Test
    void onlyConsidersDevicesOnTheRequestedFlow() {
        var snd = withDevices();

        // "Yeti" only names a capture device, so a playback switch has nothing to pick.
        snd.setDefaultDevice("Yeti", DataFlow.dfRender, Role.roleMultimedia);

        assertEquals(List.of(), snd.switchedTo);
    }

    @Test
    void aBlankReferenceLeavesTheDeviceAlone() {
        var snd = withDevices();

        snd.setDefaultDevice("  ", DataFlow.dfRender, Role.roleMultimedia);

        assertEquals(List.of(), snd.switchedTo);
    }

    @Test
    void anEndpointIdPicksOneOfTwoDevicesWithTheSameName() {
        var snd = withDevices();
        var twinA = "{0.0.0.00000000}.{11111111-0000-0000-0000-000000000000}";
        var twinB = "{0.0.0.00000000}.{22222222-0000-0000-0000-000000000000}";
        snd.deviceAdded("Headphones (USB Audio)", twinA, 1f, false, DataFlow.dfRender.ordinal());
        snd.deviceAdded("Headphones (USB Audio)", twinB, 1f, false, DataFlow.dfRender.ordinal());

        snd.setDefaultDevice(twinA, DataFlow.dfRender, Role.roleMultimedia);
        snd.setDefaultDevice(twinB, DataFlow.dfRender, Role.roleCommunications);

        assertEquals(List.of(twinA, twinB), snd.switchedTo);
    }

    @Test
    void aConnectedIdWinsOverTheNameStoredWithIt() {
        var snd = withDevices();

        snd.setDefaultDevice(SPEAKERS_ID, "Headset", DataFlow.dfRender, Role.roleMultimedia);

        assertEquals(List.of(SPEAKERS_ID), snd.switchedTo);
    }

    @Test
    void anIdThatIsNotConnectedFallsBackToItsName() {
        var snd = withDevices();

        snd.setDefaultDevice("{0.0.0.00000000}.{99999999-0000-0000-0000-000000000000}", "Headset", DataFlow.dfRender, Role.roleMultimedia);

        assertEquals(List.of(HEADSET_ID), snd.switchedTo);
    }

    @Test
    void theNameFallbackOnlyConsidersDevicesOnTheRequestedFlow() {
        var snd = withDevices();

        snd.setDefaultDevice("{0.0.0.00000000}.{99999999-0000-0000-0000-000000000000}", "Yeti", DataFlow.dfRender, Role.roleMultimedia);

        assertEquals(List.of(), snd.switchedTo);
    }

    @Test
    void aBlankReferenceIgnoresItsName() {
        var snd = withDevices();

        snd.setDefaultDevice("", "Headset", DataFlow.dfRender, Role.roleMultimedia);

        assertEquals(List.of(), snd.switchedTo);
    }

    @Test
    void anUnknownReferenceChangesNothing() {
        var snd = withDevices();

        snd.setDefaultDevice("Nonexistent", DataFlow.dfRender, Role.roleMultimedia);

        assertEquals(List.of(), snd.switchedTo);
    }

    private static RecordingSndCtrl withDevices() {
        var snd = new RecordingSndCtrl();
        // deviceAdded is the real JNI callback, so this populates devices exactly as the DLL would.
        snd.deviceAdded("Speakers (2- Realtek(R) Audio)", SPEAKERS_ID, 1f, false, DataFlow.dfRender.ordinal());
        snd.deviceAdded("Headset", HEADSET_ID, 1f, false, DataFlow.dfRender.ordinal());
        snd.deviceAdded("Headset Earphone (3- Wireless)", "other-render", 1f, false, DataFlow.dfRender.ordinal());
        snd.deviceAdded("Microphone (Yeti Stereo Microphone)", YETI_ID, 1f, false, DataFlow.dfCapture.ordinal());
        return snd;
    }

    /** Records the endpoints the real class would hand to the DLL, so no native library is needed. */
    private static final class RecordingSndCtrl extends SndCtrlWindows {
        private final List<String> switchedTo = new ArrayList<>();

        @Override
        void applyDefaultDevice(String deviceId, DataFlow flow, Role role) {
            switchedTo.add(deviceId);
        }
    }
}
