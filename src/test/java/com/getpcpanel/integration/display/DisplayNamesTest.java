package com.getpcpanel.integration.display;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DisplayNamesTest {
    @Test
    void putsTheModelFirst() {
        assertEquals("DELL S3221QS · Display 3 · 3840×2160 · main", DisplayNames.format("DELL S3221QS", 3, 3840, 2160, "main"));
    }

    @Test
    void leavesOutWhatIsUnknown() {
        assertEquals("Display 2", DisplayNames.format(null, 2, 0, 0));
        assertEquals("LG HDR WQHD", DisplayNames.format(" LG HDR WQHD ", 0, 0, 0, "", null));
        assertEquals("Display", DisplayNames.format("", 0, 0, 0));
    }
}
