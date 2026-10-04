package com.getpcpanel.rest.model.dto;

import javax.annotation.Nullable;

/**
 * An installed app the "Open app" action can start.
 *
 * @param target what opens it (the shortcut, desktop entry or app bundle)
 * @param exe    the executable or window class its windows are found by
 * @param icon   a {@code data:} PNG, when one could be read
 */
public record InstalledAppDto(String name, String target, String exe, @Nullable String icon) {
}
