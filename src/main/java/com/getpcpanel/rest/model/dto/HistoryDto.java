package com.getpcpanel.rest.model.dto;

import java.util.List;

/**
 * Whether there is a configuration change to undo or redo.
 *
 * @param changed after an undo or redo: what it changed, in a few words ({@code K3 actions · Gaming}); else empty
 */
public record HistoryDto(boolean canUndo, boolean canRedo, List<String> changed) {
}
