package com.getpcpanel.util.tray.mac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

/**
 * Runs the menu bar's Objective-C plumbing against the real runtime, off the main thread (no application object, no
 * event loop): the callback class must reach Java with the clicked item's tag and answer the application delegate's
 * quit request, and the bundled icon must decode as an image. Enable with {@code -Dpcpanel.mactest=true}.
 */
@EnabledOnOs(OS.MAC)
@EnabledIfSystemProperty(named = "pcpanel.mactest", matches = "true")
class MacAppKitMacTest {
    @Test
    void callbacksReachTheHandler() {
        NativeLibrary.getInstance("AppKit");
        var tags = new ArrayList<Integer>();
        var events = new ArrayList<String>();
        var targetClass = MacAppKit.createTargetClass(new RecordingHandler(tags, events));
        var objc = MacAppKit.Objc.INSTANCE;
        var target = objc.objc_msgSend(objc.objc_msgSend(targetClass, objc.sel_registerName("alloc")), objc.sel_registerName("init"));

        var item = objc.objc_msgSend(objc.objc_getClass("NSMenuItem"), objc.sel_registerName("new"));
        objc.objc_msgSend(item, objc.sel_registerName("setTag:"), 3L);
        objc.objc_msgSend(target, objc.sel_registerName("menuItemClicked:"), item);
        var reply = objc.objc_msgSend(target, objc.sel_registerName("applicationShouldTerminate:"), Pointer.NULL);

        assertEquals(List.of(3), tags);
        assertEquals(List.of("terminate"), events);
        assertEquals(2, Pointer.nativeValue(reply)); // NSTerminateLater
        assertFalse(MacAppKit.isMainThread());
    }

    @Test
    void bundledIconDecodes() throws Exception {
        NativeLibrary.getInstance("AppKit");
        byte[] png;
        try (var in = MacAppKitMacTest.class.getResourceAsStream("/assets/menubar-template.png")) {
            assertNotNull(in);
            png = in.readAllBytes();
        }
        var objc = MacAppKit.Objc.INSTANCE;
        var pool = objc.objc_autoreleasePoolPush();
        try {
            var image = MacAppKit.image(png);
            assertEquals(1, Pointer.nativeValue(objc.objc_msgSend(image, objc.sel_registerName("isValid"))) & 0xff);
        } finally {
            objc.objc_autoreleasePoolPop(pool);
        }
    }

    private record RecordingHandler(List<Integer> tags, List<String> events) implements MacAppKit.Handler {
        @Override
        public void menuItemClicked(int tag) {
            tags.add(tag);
        }

        @Override
        public void reopen() {
            events.add("reopen");
        }

        @Override
        public void terminateRequested() {
            events.add("terminate");
        }
    }
}
