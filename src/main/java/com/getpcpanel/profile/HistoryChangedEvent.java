package com.getpcpanel.profile;

/** What can be undone or redone changed: after an edit, its write, an undo, a redo or a checkpoint. */
public record HistoryChangedEvent(boolean canUndo, boolean canRedo) {
}
