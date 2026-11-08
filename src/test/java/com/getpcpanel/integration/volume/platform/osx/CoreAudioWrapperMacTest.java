package com.getpcpanel.integration.volume.platform.osx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import com.sun.jna.Memory;
import com.sun.jna.ptr.IntByReference;

/** Runs against the real CoreAudio HAL; enable with {@code -Dpcpanel.mactest=true}. */
@EnabledOnOs(OS.MAC)
@EnabledIfSystemProperty(named = "pcpanel.mactest", matches = "true")
class CoreAudioWrapperMacTest {
    @Test
    void initAllowsIdleSleepDuringAudioIo() {
        new CoreAudioWrapper().init();

        try (var data = new Memory(Integer.BYTES)) {
            var size = new IntByReference(Integer.BYTES);
            var address = new CoreAudioLib.AudioObjectPropertyAddress(CoreAudioWrapper.PROP_SLEEPING_IS_ALLOWED, CoreAudioWrapper.SCOPE_GLOBAL, 0);
            assertEquals(0, CoreAudioLib.INSTANCE.AudioObjectGetPropertyData(CoreAudioLib.kAudioObjectSystemObject, address, 0, null, size, data));
            assertEquals(1, data.getInt(0));
        }
    }
}
