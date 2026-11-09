package com.getpcpanel.integration.keyboard.platform.osx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.getpcpanel.integration.keyboard.command.CommandMedia.VolumeButton;
import com.sun.jna.Pointer;

class OsxSystemMediaKeysTest {
    @ParameterizedTest
    @CsvSource({"playPause,16,0x100A00,0x100B00", "next,19,0x130A00,0x130B00", "prev,20,0x140A00,0x140B00"})
    void physicalMediaKeyPayloads(VolumeButton button, int code, String down, String up) {
        assertEquals(code, OsxSystemMediaKeys.keyCode(button));
        assertEquals(Long.decode(down), OsxSystemMediaKeys.data1(code, true));
        assertEquals(Long.decode(up), OsxSystemMediaKeys.data1(code, false));
    }

    @Test
    void completePairIsPreparedBeforePostingAndPoolOutlivesBothPosts() {
        var events = new RecordingEvents();

        new OsxSystemMediaKeys(events).send(VolumeButton.playPause);

        assertEquals(List.of("pushPool", "create 16 down", "create 16 up", "cgEvent 2", "cgEvent 3", "post 4", "post 5", "popPool 1"),
                events.calls);
    }

    @Test
    void failedReleaseConstructionDoesNotPostAPartialPressAndReleasesPool() {
        var events = new RecordingEvents();
        events.created.put(false, null);

        assertThrows(IllegalStateException.class, () -> new OsxSystemMediaKeys(events).send(VolumeButton.playPause));

        assertEquals(List.of("pushPool", "create 16 down", "create 16 up", "popPool 1"), events.calls);
    }

    @Test
    void failedCgConversionDoesNotPostAPartialPressAndReleasesPool() {
        var events = new RecordingEvents();
        events.converted.put(3L, null);

        assertThrows(IllegalStateException.class, () -> new OsxSystemMediaKeys(events).send(VolumeButton.playPause));

        assertEquals(List.of("pushPool", "create 16 down", "create 16 up", "cgEvent 2", "cgEvent 3", "popPool 1"), events.calls);
    }

    @Test
    void unsupportedActionDoesNotLoadNativeFrameworks() {
        var events = new RecordingEvents();

        assertThrows(IllegalArgumentException.class, () -> new OsxSystemMediaKeys(events).send(VolumeButton.stop));

        assertEquals(List.of(), events.calls);
    }

    /**
     * Writes down every call with its arguments, as the address of each pointer. A hand-written fake rather than a
     * Mockito mock: {@code Events} is package-private, and Mockito cannot mock a package-private interface under the
     * native-image tracing agent (the CI metadata run). The pool is 1, the press 2 and the release 3, converted to
     * 4 and 5; an entry in {@link #created} or {@link #converted} overrides that, {@code null} for a failure.
     */
    private static final class RecordingEvents implements OsxSystemMediaKeys.Events {
        final List<String> calls = new ArrayList<>();
        final Map<Boolean, Pointer> created = new HashMap<>(Map.of(true, Pointer.createConstant(2), false, Pointer.createConstant(3)));
        final Map<Long, Pointer> converted = new HashMap<>(Map.of(2L, Pointer.createConstant(4), 3L, Pointer.createConstant(5)));

        @Override
        public Pointer pushPool() {
            calls.add("pushPool");
            return Pointer.createConstant(1);
        }

        @Override
        public void popPool(Pointer pool) {
            calls.add("popPool " + address(pool));
        }

        @Override
        public @Nullable Pointer create(int key, boolean down) {
            calls.add("create " + key + (down ? " down" : " up"));
            return created.get(down);
        }

        @Override
        public @Nullable Pointer cgEvent(Pointer event) {
            calls.add("cgEvent " + address(event));
            return converted.get(address(event));
        }

        @Override
        public void post(Pointer event) {
            calls.add("post " + address(event));
        }

        private static long address(Pointer p) {
            return Pointer.nativeValue(p);
        }
    }
}
