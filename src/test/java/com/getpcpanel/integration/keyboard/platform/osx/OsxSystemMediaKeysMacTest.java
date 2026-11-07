package com.getpcpanel.integration.keyboard.platform.osx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.getpcpanel.integration.keyboard.command.CommandMedia.VolumeButton;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

/** Constructs and inspects real NSEvents without posting them or changing playback. */
@EnabledOnOs(OS.MAC)
class OsxSystemMediaKeysMacTest {
    @ParameterizedTest
    @CsvSource({"playPause,true", "playPause,false", "next,true", "next,false", "prev,true", "prev,false"})
    void appKitReceivesTheCorrectByValuePointAndMediaPayload(VolumeButton button, boolean down) {
        var events = new OsxSystemMediaKeys.CocoaEvents();
        var pool = events.pushPool();
        try {
            var event = events.create(OsxSystemMediaKeys.keyCode(button), down);
            assertNotNull(event);
            assertEquals(16, new OsxSystemMediaKeys.Point().size());
            assertEquals(14, integerProperty(event, "type"));
            assertEquals(8, integerProperty(event, "subtype") & 0xFFFFL);
            assertEquals(OsxSystemMediaKeys.data1(OsxSystemMediaKeys.keyCode(button), down), integerProperty(event, "data1"));
            assertEquals(-1, integerProperty(event, "data2"));
            assertNotNull(events.cgEvent(event));
        } finally {
            events.popPool(pool);
        }
    }

    private static long integerProperty(Pointer event, String name) {
        var selector = OsxSystemMediaKeys.Objc.INSTANCE.sel_registerName(name);
        return NativeLibrary.getInstance("objc").getFunction("objc_msgSend").invokeLong(new Object[] {event, selector});
    }
}
