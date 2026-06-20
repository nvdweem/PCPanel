package com.getpcpanel.device.provider.midi;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.getpcpanel.device.provider.midi.MidiProtocol.MidiKind;

/**
 * The control-index assigner: the rest of the app keys every control by a single flat {@code int}
 * (Profile maps, {@code AnalogInputSpec.index}, {@code KnobRotateEvent.knob}), but a MIDI control is
 * identified by the triple {@code (channel, type CC|NOTE, number)}. This learns a stable, sequential
 * {@code int} index for each distinct triple the first time it is seen and remembers it, so indices
 * stay constant across messages and (via {@link #toPersisted()}/{@link #fromPersisted}) across
 * restarts.
 *
 * <p>CC (analog) and NOTE (digital) live in <em>separate</em> index spaces so a CC and a note never
 * collide: each space counts up from 0 independently. The descriptor encodes the MIDI identity in
 * the spec id (e.g. {@code "cc7.ch0"}, {@code "note36.ch9"}).
 *
 * <p>Pure and hardware-free: no {@code javax.sound.midi} types, fully unit-testable.
 */
public final class MidiControlIndex {
    /** Insertion-ordered so re-assigning sequential indices after rehydration is deterministic. */
    private final Map<String, Integer> ccIndex = new LinkedHashMap<>();
    private final Map<String, Integer> noteIndex = new LinkedHashMap<>();
    private int nextCc;
    private int nextNote;

    /** The stable key for a CC: {@code "cc<number>.ch<channel>"} (e.g. {@code "cc7.ch0"}). */
    public static String ccId(int channel, int ccNumber) {
        return "cc" + ccNumber + ".ch" + channel;
    }

    /** The stable key for a note: {@code "note<number>.ch<channel>"} (e.g. {@code "note36.ch9"}). */
    public static String noteId(int channel, int note) {
        return "note" + note + ".ch" + channel;
    }

    /** The stable id for a decoded event's identity (CC vs NOTE chosen by {@code kind}). */
    public static String idFor(MidiKind kind, int channel, int number) {
        return kind == MidiKind.ANALOG ? ccId(channel, number) : noteId(channel, number);
    }

    /**
     * Returns the stable index for {@code (kind, channel, number)}, assigning the next free index in
     * that kind's space the first time it is seen. Idempotent: the same triple always returns the
     * same index.
     */
    public synchronized int assign(MidiKind kind, int channel, int number) {
        if (kind == MidiKind.ANALOG) {
            return ccIndex.computeIfAbsent(ccId(channel, number), k -> nextCc++);
        }
        return noteIndex.computeIfAbsent(noteId(channel, number), k -> nextNote++);
    }

    /** The already-assigned index for an id, or {@code null} if that control has not been seen. */
    @Nullable
    public synchronized Integer indexOfCc(int channel, int ccNumber) {
        return ccIndex.get(ccId(channel, ccNumber));
    }

    @Nullable
    public synchronized Integer indexOfNote(int channel, int note) {
        return noteIndex.get(noteId(channel, note));
    }

    /** The already-assigned index for a raw id string (e.g. {@code "cc7.ch0"}), or {@code null}. */
    @Nullable
    public synchronized Integer indexOfId(String id) {
        if (id.startsWith("cc")) {
            return ccIndex.get(id);
        }
        if (id.startsWith("note")) {
            return noteIndex.get(id);
        }
        return null;
    }

    public synchronized int ccCount() {
        return ccIndex.size();
    }

    public synchronized int noteCount() {
        return noteIndex.size();
    }

    /**
     * Serializes the learned mapping to a flat {@code id -> index} string map suitable for the
     * {@code DeviceSave.providerConfig} blob. CC and NOTE ids are unambiguous (different prefixes) so
     * they share one map; the per-space counters are rebuilt on {@link #fromPersisted}.
     */
    public synchronized Map<String, String> toPersisted() {
        var out = new LinkedHashMap<String, String>();
        ccIndex.forEach((id, idx) -> out.put(id, String.valueOf(idx)));
        noteIndex.forEach((id, idx) -> out.put(id, String.valueOf(idx)));
        return out;
    }

    /**
     * Rehydrates an assigner from a persisted {@code id -> index} map (see {@link #toPersisted()}).
     * Indices are preserved exactly; the next-free counters resume just past the highest seen index
     * in each space, so a freshly-seen control after restart never reuses a persisted index.
     */
    public static MidiControlIndex fromPersisted(@Nullable Map<String, String> persisted) {
        var result = new MidiControlIndex();
        if (persisted == null) {
            return result;
        }
        for (var e : persisted.entrySet()) {
            int idx;
            try {
                idx = Integer.parseInt(e.getValue().trim());
            } catch (NumberFormatException ex) {
                continue; // skip a corrupt entry rather than fail the whole rehydrate
            }
            var id = e.getKey();
            if (id.startsWith("cc")) {
                result.ccIndex.put(id, idx);
                result.nextCc = Math.max(result.nextCc, idx + 1);
            } else if (id.startsWith("note")) {
                result.noteIndex.put(id, idx);
                result.nextNote = Math.max(result.nextNote, idx + 1);
            }
        }
        return result;
    }
}
