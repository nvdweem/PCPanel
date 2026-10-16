package com.getpcpanel.util.tray.mac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.getpcpanel.util.app.CopyUiLinkEvent;
import com.getpcpanel.util.app.OpenFolderEvent;
import com.getpcpanel.util.app.ShowMainEvent;
import com.getpcpanel.util.io.FileUtil;

class MacMenuBarTest {
    private MacMenuBar menuBar;

    @BeforeEach
    void setUp() {
        menuBar = new MacMenuBar();
        menuBar.fileUtil = mock(FileUtil.class);
        when(menuBar.fileUtil.getRoot()).thenReturn(new File("/tmp/pcpanel-root"));
    }

    @Test
    void menuActionsFireTheTrayEvents() {
        assertEquals(Optional.of(new ShowMainEvent()), menuBar.eventFor(MacMenuAction.OPEN));
        assertEquals(Optional.of(new CopyUiLinkEvent()), menuBar.eventFor(MacMenuAction.COPY_LINK));
        assertEquals(Optional.of(new OpenFolderEvent(new File("/tmp/pcpanel-root").toString())), menuBar.eventFor(MacMenuAction.SETTINGS));
        assertEquals(Optional.of(new ShowMainEvent(ShowMainEvent.REPORT_PATH)), menuBar.eventFor(MacMenuAction.REPORT));
    }

    @Test
    void quitFiresNoEvent() {
        assertTrue(menuBar.eventFor(MacMenuAction.QUIT).isEmpty());
    }

    @Test
    void withoutInitTheMainThreadIsLeftToTheCaller() {
        // TrayInitializer never asked for the icon (disabled tray): no AppKit, so Main waits for exit itself.
        assertFalse(menuBar.runOnMainThread());
    }
}
