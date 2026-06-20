package com.getpcpanel.device.provider.midi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getpcpanel.device.provider.midi.MidiProtocol.MidiKind;

@DisplayName("MIDI control-index assigner (pure, persistable)")
class MidiControlIndexTest {

    @Test
    void assignsSequentialIndicesPerKind() {
        var idx = new MidiControlIndex();
        assertEquals(0, idx.assign(MidiKind.ANALOG, 0, 7));
        assertEquals(1, idx.assign(MidiKind.ANALOG, 0, 8));
        assertEquals(2, idx.assign(MidiKind.ANALOG, 1, 7)); // same CC, different channel = distinct
    }

    @Test
    void stableAcrossRepeatedCalls() {
        var idx = new MidiControlIndex();
        var first = idx.assign(MidiKind.ANALOG, 0, 7);
        assertEquals(first, idx.assign(MidiKind.ANALOG, 0, 7));
        assertEquals(first, idx.assign(MidiKind.ANALOG, 0, 7));
    }

    @Test
    void distinctIdsGetDistinctIndices() {
        var idx = new MidiControlIndex();
        var a = idx.assign(MidiKind.ANALOG, 0, 7);
        var b = idx.assign(MidiKind.ANALOG, 0, 8);
        assertNotEquals(a, b);
    }

    @Test
    void ccAndNoteNeverCollide() {
        var idx = new MidiControlIndex();
        // Both spaces start at 0; they are different index spaces, so a CC index 0 and a NOTE index 0
        // refer to different controls (the descriptor keeps them in analogInputs vs digitalInputs).
        var cc0 = idx.assign(MidiKind.ANALOG, 0, 7);
        var note0 = idx.assign(MidiKind.BUTTON, 0, 36);
        assertEquals(0, cc0);
        assertEquals(0, note0);
        // The ids are unambiguous even though the integer indices coincide across spaces.
        assertNotEquals(MidiControlIndex.idFor(MidiKind.ANALOG, 0, 7), MidiControlIndex.idFor(MidiKind.BUTTON, 0, 36));
    }

    @Test
    void idEncodesMidiIdentity() {
        assertEquals("cc7.ch0", MidiControlIndex.ccId(0, 7));
        assertEquals("note36.ch9", MidiControlIndex.noteId(9, 36));
        assertEquals("cc7.ch0", MidiControlIndex.idFor(MidiKind.ANALOG, 0, 7));
        assertEquals("note36.ch9", MidiControlIndex.idFor(MidiKind.BUTTON, 9, 36));
    }

    @Test
    void lookupReturnsNullForUnseen() {
        var idx = new MidiControlIndex();
        assertNull(idx.indexOfCc(0, 7));
        idx.assign(MidiKind.ANALOG, 0, 7);
        assertEquals(0, idx.indexOfCc(0, 7));
        assertNull(idx.indexOfNote(0, 7)); // a note with the same number is still unseen
    }

    @Test
    void persistRehydrateIsStable() {
        var idx = new MidiControlIndex();
        var cc7 = idx.assign(MidiKind.ANALOG, 0, 7);
        var cc8 = idx.assign(MidiKind.ANALOG, 0, 8);
        var note36 = idx.assign(MidiKind.BUTTON, 9, 36);

        var persisted = idx.toPersisted();
        var rehydrated = MidiControlIndex.fromPersisted(persisted);

        assertEquals(cc7, rehydrated.indexOfCc(0, 7));
        assertEquals(cc8, rehydrated.indexOfCc(0, 8));
        assertEquals(note36, rehydrated.indexOfNote(9, 36));
        // Re-assigning the same triple after rehydrate returns the persisted index unchanged.
        assertEquals(cc7, rehydrated.assign(MidiKind.ANALOG, 0, 7));
        assertEquals(note36, rehydrated.assign(MidiKind.BUTTON, 9, 36));
    }

    @Test
    void rehydratedCountersResumePastHighestIndex() {
        var idx = new MidiControlIndex();
        idx.assign(MidiKind.ANALOG, 0, 7); // 0
        idx.assign(MidiKind.ANALOG, 0, 8); // 1
        var rehydrated = MidiControlIndex.fromPersisted(idx.toPersisted());

        // A freshly-seen CC after restart gets the next free index, never reusing a persisted one.
        assertEquals(2, rehydrated.assign(MidiKind.ANALOG, 0, 9));
    }

    @Test
    void fromNullPersistedIsEmpty() {
        var idx = MidiControlIndex.fromPersisted(null);
        assertEquals(0, idx.ccCount());
        assertEquals(0, idx.noteCount());
        assertEquals(0, idx.assign(MidiKind.ANALOG, 0, 7));
    }
}
