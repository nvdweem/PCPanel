package com.getpcpanel.integration.display.platform.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class WinDisplayPowerTest {
    private static final String PATH = "\\\\?\\DISPLAY#GSM5B7F#5&2b1b3c8a&0&UID4352#{e6f07b5f-ee97-4a90-b076-33f57bf4eaa7}";

    @Test
    void namesADisplayByModelNumberAndResolution() {
        assertEquals("DELL S3221QS · Display 3 · 3840×2160", WinDisplayPower.name("DELL S3221QS", "\\\\.\\DISPLAY3", 3840, 2160, 0, 1, false));
        assertEquals("Display 12 · 1920×1080", WinDisplayPower.name(null, "\\\\.\\DISPLAY12", 1920, 1080, 0, 1, false), "a monitor without EDID");
    }

    @Test
    void marksTheMainDisplay() {
        assertEquals("SAMSUNG · Display 1 · 2560×1440 · main", WinDisplayPower.name("SAMSUNG", "\\\\.\\DISPLAY1", 2560, 1440, 0, 1, true));
    }

    @Test
    void numbersTheMonitorsOfAClonedOutput() {
        assertEquals("Display 1 · 1920×1080 · 2 · main", WinDisplayPower.name("", "\\\\.\\DISPLAY1", 1920, 1080, 1, 2, true));
    }

    @Test
    void matchesDevicePathsWhateverTheirCase() {
        assertEquals(WinDisplayPower.pathKey(PATH), WinDisplayPower.pathKey(PATH.toUpperCase(Locale.ROOT)));
    }

    @Test
    void identifiesAMonitorByItsDevicePath() {
        assertEquals(PATH, WinDisplayPower.id("\\\\.\\DISPLAY1", List.of(PATH), 0));
    }

    @Test
    void fallsBackToTheOutputAndIndex() {
        assertEquals("\\\\.\\DISPLAY1#1", WinDisplayPower.id("\\\\.\\DISPLAY1", List.of(PATH), 1));
        assertEquals("\\\\.\\DISPLAY3#0", WinDisplayPower.id("\\\\.\\DISPLAY3", List.of(), 0));
    }
}
