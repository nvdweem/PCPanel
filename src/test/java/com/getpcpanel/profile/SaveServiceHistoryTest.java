package com.getpcpanel.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.nio.file.Path;
import java.util.concurrent.CompletionStage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.getpcpanel.AppLikeMapper;
import com.getpcpanel.Json;
import com.getpcpanel.device.DeviceHolder;
import com.getpcpanel.device.provider.pcpanel.DeviceType;
import com.getpcpanel.template.TemplateSaveMigration;
import com.getpcpanel.util.io.FileUtil;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.util.TypeLiteral;

class SaveServiceHistoryTest {
    @TempDir Path tmp;
    private SaveService sut;

    @BeforeEach
    void setUp() throws Exception {
        var fileUtil = new FileUtil();
        var root = FileUtil.class.getDeclaredField("rootPath");
        root.setAccessible(true);
        root.set(fileUtil, tmp.toString());
        var ensure = FileUtil.class.getDeclaredMethod("ensureRoot");
        ensure.setAccessible(true);
        ensure.invoke(fileUtil);

        var json = new Json();
        var mapper = Json.class.getDeclaredField("mapper");
        mapper.setAccessible(true);
        mapper.set(json, AppLikeMapper.build());

        sut = new SaveService();
        sut.fileUtil = fileUtil;
        sut.json = json;
        sut.devices = new DeviceHolder();
        sut.templateMigration = new TemplateSaveMigration();
        sut.eventBus = new NoEvents();
        sut.debouncer = new com.getpcpanel.util.concurrent.Debouncer();
        sut.load();

        sut.get().createSaveForNewDevice("dev", DeviceType.PCPANEL_PRO);
        var ds = sut.get().getDeviceSave("dev");
        ds.getProfiles().add(new Profile("A", DeviceType.PCPANEL_PRO));
        ds.getProfiles().add(new Profile("B", DeviceType.PCPANEL_PRO));
        ds.setCurrentProfileName("A");
        sut.save(); // the starting point
    }

    @Test
    void undoAndRedoAnEdit() {
        sut.checkpoint();
        sut.get().setDblClickInterval(300L);
        sut.save();
        assertTrue(sut.canUndo());

        assertTrue(sut.undo());
        assertEquals(500L, sut.get().getDblClickInterval());
        assertTrue(sut.canRedo());

        assertTrue(sut.redo());
        assertEquals(300L, sut.get().getDblClickInterval());
    }

    @Test
    void aProfileSwitchIsNotAnEdit() {
        while (sut.undo()) {
            // back to the start, with nothing left to undo
        }
        assertFalse(sut.canUndo());
        sut.get().getDeviceSave("dev").setCurrentProfileName("B");
        sut.save();
        assertFalse(sut.canUndo(), "switching profiles adds nothing to undo");
    }

    @Test
    void undoKeepsTheActiveProfile() {
        sut.checkpoint();
        sut.get().setDblClickInterval(300L);
        sut.save();
        sut.get().getDeviceSave("dev").setCurrentProfileName("B");
        sut.save();

        assertTrue(sut.undo());
        assertEquals(500L, sut.get().getDblClickInterval());
        assertEquals("B", sut.get().getDeviceSave("dev").getCurrentProfileName());
    }

    @Test
    void changesBetweenCheckpointsAreOneStep() {
        sut.checkpoint();
        sut.get().setDblClickInterval(300L);
        sut.save();
        sut.get().setDblClickInterval(301L);
        sut.save();
        sut.checkpoint();
        sut.get().setDblClickInterval(302L);
        sut.save();

        sut.undo();
        assertEquals(301L, sut.get().getDblClickInterval(), "the change after the checkpoint");
        sut.undo();
        assertEquals(500L, sut.get().getDblClickInterval(), "300 and 301 were one step");
    }

    @Test
    void anEditClearsRedo() {
        sut.get().setDblClickInterval(300L);
        sut.save();
        sut.undo();
        sut.checkpoint();
        sut.get().setDblClickInterval(400L);
        sut.save();
        assertFalse(sut.canRedo());
    }

    @Test
    void aCheckpointKeepsAStillUnwrittenChangeInItsStep() {
        sut.checkpoint();
        sut.get().setDblClickInterval(300L);
        sut.save();
        sut.get().setDblClickInterval(301L); // still waiting for the debounced write when the user moves on
        sut.checkpoint();
        sut.get().setDblClickInterval(302L);
        sut.save();

        sut.undo();
        assertEquals(301L, sut.get().getDblClickInterval());
        sut.undo();
        assertEquals(500L, sut.get().getDblClickInterval(), "300 and 301 were one step");
    }

    @Test
    void undoTakesBackAStillUnwrittenChange() {
        sut.checkpoint();
        sut.get().setDblClickInterval(300L);
        sut.save();
        sut.checkpoint();
        sut.get().setDblClickInterval(301L); // still waiting for the debounced write

        sut.undo();
        assertEquals(300L, sut.get().getDblClickInterval(), "the unwritten change is the step undone");
    }

    @Test
    void aChangeCanBeUndoneBeforeItIsWritten() {
        sut.checkpoint();
        sut.get().setDblClickInterval(300L);
        sut.save();
        sut.undo();
        assertTrue(sut.canRedo());

        sut.get().setDblClickInterval(301L);
        sut.debouncedSave(); // written a second from now
        assertTrue(sut.canUndo());
        assertFalse(sut.canRedo(), "the edit clears what there was to redo");
    }

    @Test
    void undoSaysWhatItChanged() {
        sut.checkpoint();
        sut.get().setDblClickInterval(300L);
        sut.save();
        sut.undo();
        assertEquals(java.util.List.of("Settings"), sut.lastChange());
    }

    private static final class NoEvents implements Event<Object> {
        @Override public void fire(Object event) { }
        @Override public <U> CompletionStage<U> fireAsync(U event) { throw new UnsupportedOperationException(); }
        @Override public <U> CompletionStage<U> fireAsync(U event, NotificationOptions options) { throw new UnsupportedOperationException(); }
        @Override public Event<Object> select(Annotation... qualifiers) { return this; }
        @Override public <U> Event<U> select(Class<U> subtype, Annotation... qualifiers) { throw new UnsupportedOperationException(); }
        @Override public <U> Event<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) { throw new UnsupportedOperationException(); }
    }
}
