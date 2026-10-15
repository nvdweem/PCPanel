package com.getpcpanel.integration.volume.platform.osx;

import static com.getpcpanel.integration.volume.platform.osx.CoreAudioWrapper.PROP_DEFAULT_INPUT_DEVICE;
import static com.getpcpanel.integration.volume.platform.osx.CoreAudioWrapper.PROP_DEFAULT_OUTPUT_DEVICE;
import static com.getpcpanel.integration.volume.platform.osx.CoreAudioWrapper.PROP_DEVICES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.getpcpanel.platform.process.OsxProcessHelper;

import jakarta.enterprise.event.Event;

/** The CoreAudio notifications {@link SndCtrlOsx} listens to, and what each one sets off. */
class SndCtrlOsxNotificationTest {
    private ListeningCoreAudio coreAudio;
    private OsxProcessTapService tapService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        coreAudio = new ListeningCoreAudio();
        tapService = mock(OsxProcessTapService.class);
        new SndCtrlOsx(coreAudio, mock(OsxProcessHelper.class), tapService, mock(Event.class)).init();
    }

    @Test
    void defaultOutputChangeMovesTheProcessTaps() {
        var reads = coreAudio.deviceReads.get();

        coreAudio.fire(PROP_DEFAULT_OUTPUT_DEVICE);

        verify(tapService).defaultOutputChanged();
        assertEquals(reads + 1, coreAudio.deviceReads.get(), "the devices are read again");
    }

    @Test
    void otherDeviceChangesLeaveTheProcessTapsAlone() {
        coreAudio.fire(PROP_DEFAULT_INPUT_DEVICE);
        coreAudio.fire(PROP_DEVICES);

        verify(tapService, never()).defaultOutputChanged();
    }

    /** CoreAudio without devices that keeps the system-object listeners so a test can fire them. */
    private static final class ListeningCoreAudio extends CoreAudioWrapper {
        private final Map<Integer, Runnable> systemListeners = new ConcurrentHashMap<>();
        final AtomicInteger deviceReads = new AtomicInteger();

        @Override
        public ListenerHandle addListener(int objectId, int selector, int scope, Runnable callback) {
            if (objectId == CoreAudioLib.kAudioObjectSystemObject) {
                systemListeners.put(selector, callback);
            }
            return new ListenerHandle(objectId, null, null);
        }

        @Override
        public void removeListener(@Nullable ListenerHandle handle) {
        }

        @Override
        public List<CoreAudioDevice> getDevices() {
            deviceReads.incrementAndGet();
            return List.of();
        }

        void fire(int selector) {
            systemListeners.get(selector).run();
        }
    }
}
