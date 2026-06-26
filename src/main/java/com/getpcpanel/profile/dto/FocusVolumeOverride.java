package com.getpcpanel.profile.dto;

import java.util.List;

/**
 * A focus-volume redirection rule. When any of {@code sources} (process exe names, e.g. {@code steam.exe})
 * holds OS focus, the Focused-app volume dial controls every entry in {@code targets} instead of the
 * focused app's own audio session.
 *
 * <p>{@code includeSource} additionally applies the dial to the source app's own OS volume; when it is
 * {@code false} the source itself is left untouched. That covers apps that never emit audio themselves
 * but spawn a helper that does (Steam → SteamWebHelper): redirect to the helper without fighting over
 * Steam's (silent) session. Issue #49.
 */
public record FocusVolumeOverride(List<String> sources, List<FocusVolumeTarget> targets, boolean includeSource) {
    public List<String> sources() {
        return sources == null ? List.of() : sources;
    }

    public List<FocusVolumeTarget> targets() {
        return targets == null ? List.of() : targets;
    }
}
