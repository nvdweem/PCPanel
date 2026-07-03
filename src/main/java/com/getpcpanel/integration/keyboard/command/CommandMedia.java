package com.getpcpanel.integration.keyboard.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.command.ButtonAction;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.meta.CommandCategory;
import com.getpcpanel.commands.meta.CommandKind;
import com.getpcpanel.commands.meta.CommandMeta;
import com.getpcpanel.integration.keyboard.Keyboard;
import com.getpcpanel.util.CdiHelper;

import lombok.Getter;
import lombok.ToString;

/**
 * Button action that presses a multimedia key (play/pause, next, prev, stop, mute), optionally aimed
 * at specific applications. {@link #apps} is an ordered preference list of target executables (e.g.
 * {@code "Spotify.exe"}): on Windows the first one running receives the action directly, and only if
 * none are running is the global media key posted — so a browser can't hijack the key from Spotify.
 * This per-app targeting is a Windows feature; other platforms fall back to the global media key (see
 * {@link Keyboard#sendMediaKey}). All platform behaviour lives in the {@link Keyboard} backend.
 */
@Getter
@ToString(callSuper = true)
@JsonTypeName("keyboard.media")
@CommandMeta(label = "Media", category = CommandCategory.system, kinds = {CommandKind.button}, icon = "play", legacyIds = {"com.getpcpanel.commands.command.CommandMedia"})
public class CommandMedia extends Command implements ButtonAction {
    private final VolumeButton button;
    /** Ordered preferred target executables (Windows); empty means always post the global media key. */
    private final List<String> apps;

    /** Cross-platform media-button identifier; each {@link Keyboard} backend maps it to its native key. */
    public enum VolumeButton {
        mute, next, prev, stop, playPause;

        public static Optional<VolumeButton> tryValueOf(String name) {
            try {
                return Optional.of(valueOf(name));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
    }

    @JsonCreator
    public CommandMedia(@JsonProperty("button") VolumeButton button, @JsonProperty("apps") List<String> apps, @JsonProperty("spotify") boolean spotify) {
        this.button = button;
        var resolved = apps == null ? new ArrayList<String>() : new ArrayList<>(apps);
        if (resolved.isEmpty() && spotify) {
            resolved.add("Spotify.exe"); // migrate the legacy boolean "Spotify-aware" flag to the app list
        }
        this.apps = List.copyOf(resolved);
    }

    @Override
    public void execute() {
        if (button == null) {
            return;
        }
        CdiHelper.getBean(Keyboard.class).sendMediaKey(button, apps);
    }

    @Override
    public String buildLabel() {
        var targets = apps.stream().map(a -> StringUtils.removeEndIgnoreCase(a, ".exe")).toList();
        return button + (targets.isEmpty() ? "" : " (" + String.join(", ", targets) + ")");
    }
}
