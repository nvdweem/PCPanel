package com.getpcpanel.discord.command;

import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.commands.command.DialAction;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

/**
 * Volume in Discord, with a dial. {@code target} is {@link #MIC} (your mic input), {@link #OUTPUT} (how
 * loud you hear everyone), or another member's username (how loud you hear them). {@code clearMuteOnChange}
 * also clears the matching mute/deafen when the dial moves: unmute for mic, undeafen for output, locally
 * unmute that user.
 */
@Getter
@Log4j2
@ToString(callSuper = true)
public final class CommandDiscordVolume extends CommandDiscord implements DialAction {
    public static final String MIC = "mic";
    public static final String OUTPUT = "output";

    @Nullable private final String target;
    private final boolean clearMuteOnChange;
    @Nullable private final DialCommandParams dialParams;

    @JsonCreator
    public CommandDiscordVolume(
            @JsonProperty("target") @Nullable String target,
            @JsonProperty("clearMuteOnChange") boolean clearMuteOnChange,
            @JsonProperty("dialParams") @Nullable DialCommandParams dialParams) {
        this.target = target;
        this.clearMuteOnChange = clearMuteOnChange;
        this.dialParams = dialParams;
    }

    private boolean isMic() {
        return StringUtils.isBlank(target) || MIC.equals(target);
    }

    @Override
    public String buildLabel() {
        var t = isMic() ? "mic" : OUTPUT.equals(target) ? "output" : target;
        return "Discord — " + t + " volume";
    }

    @Override
    public void execute(DialActionParameters context) {
        var service = getDiscordService();
        if (!service.isAuthenticated()) {
            log.warn("Not sending command, Discord not connected/authenticated");
            return;
        }
        var fraction = context.dial().getValue(this, 0, 1);
        if (isMic()) {
            service.applyInputVolume(fraction, clearMuteOnChange);
        } else if (OUTPUT.equals(target)) {
            service.applyOutputVolume(fraction, clearMuteOnChange);
        } else {
            service.applyUserVolume(target, fraction, clearMuteOnChange);
        }
    }
}
