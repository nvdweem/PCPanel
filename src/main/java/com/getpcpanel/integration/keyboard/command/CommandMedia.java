package com.getpcpanel.integration.keyboard.command;

import java.util.Optional;

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
 * Button action that presses a multimedia key (play/pause, next, prev, stop, mute), optionally
 * targeting Spotify. All platform behaviour lives in the {@link Keyboard} backend — this command just
 * carries the chosen button and forwards it.
 */
@Getter
@ToString(callSuper = true)
@JsonTypeName("keyboard.media")
@CommandMeta(label = "Media", category = CommandCategory.system, kinds = {CommandKind.button}, icon = "play", legacyIds = {"com.getpcpanel.commands.command.CommandMedia"})
public class CommandMedia extends Command implements ButtonAction {
    private final VolumeButton button;
    private final boolean spotify;

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
    public CommandMedia(@JsonProperty("button") VolumeButton button, @JsonProperty("spotify") boolean spotify) {
        this.button = button;
        this.spotify = spotify;
    }

    @Override
    public void execute() {
        if (button == null) {
            return;
        }
        CdiHelper.getBean(Keyboard.class).sendMediaKey(button, spotify);
    }

    @Override
    public String buildLabel() {
        return button + (spotify ? " (Spotify)" : "");
    }
}
