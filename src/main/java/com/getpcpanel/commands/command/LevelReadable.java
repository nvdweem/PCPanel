package com.getpcpanel.commands.command;

import javax.annotation.Nullable;

/** A dial action whose target's current level can be read back, in the same 0..1 domain the action sets. */
public interface LevelReadable {
    /** The target's level right now, or null when it is unknown (target not running, not resolvable). */
    @Nullable
    Float readLevel();

    /**
     * What {@link #readLevel()} reads now, when that can change while the control stays put (the focused app); null
     * for a fixed target. Another value is another target, which "no volume jumps" treats as changed elsewhere.
     */
    @Nullable
    default Object levelTarget() {
        return null;
    }
}
