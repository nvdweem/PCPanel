package com.getpcpanel.discord.command;

import java.util.List;
import java.util.OptionalInt;

import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.commands.command.ButtonAction;
import com.getpcpanel.cpp.FocusProcessService;
import com.getpcpanel.discord.DiscordService;
import com.getpcpanel.util.CdiHelper;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

/**
 * Toggle Discord screen sharing. {@link Mode#SCREEN} shares via Discord's own share picker; {@link Mode#PROCESS}
 * and {@link Mode#FOCUS} share a specific window directly, resolving the chosen / focused window to its PID via
 * {@link FocusProcessService}. Toggling again stops the share.
 */
@Getter
@Log4j2
@ToString(callSuper = true)
public final class CommandDiscordScreenShare extends CommandDiscord implements ButtonAction {
    public enum Mode { SCREEN, PROCESS, FOCUS }

    private final Mode mode;
    private final List<String> processName;
    @Nullable private final String overlayText;

    @JsonCreator
    public CommandDiscordScreenShare(
            @JsonProperty("mode") @Nullable Mode mode,
            @JsonProperty("processName") @Nullable List<String> processName,
            @JsonProperty("overlayText") @Nullable String overlayText) {
        this.mode = mode == null ? Mode.SCREEN : mode;
        this.processName = processName == null ? List.of() : processName;
        this.overlayText = overlayText;
    }

    @Override
    public String buildLabel() {
        return switch (mode) {
            case SCREEN -> "Discord — share screen";
            case FOCUS -> "Discord — share focused app";
            case PROCESS -> "Discord — share " + processName.stream().findFirst().orElse("app");
        };
    }

    @Override
    public void execute() {
        var service = getDiscordService();
        if (!service.isAuthenticated()) {
            log.warn("Not sending command, Discord not connected/authenticated");
            return;
        }
        if (mode == Mode.SCREEN) {
            share(service, null); // null PID → Discord shows its own "what to share" picker
            return;
        }
        var pids = CdiHelper.getBean(FocusProcessService.class);
        var pid = mode == Mode.FOCUS
                ? pids.foregroundPid()
                : pids.pidForExecutable(processName.stream().filter(StringUtils::isNotBlank).findFirst().orElse(""));
        if (pid.isEmpty()) {
            log.warn("Discord screen share: couldn't resolve a PID for {} — nothing to share",
                    mode == Mode.FOCUS ? "the focused window" : processName);
            return;
        }
        share(service, pid.getAsInt());
    }

    private void share(DiscordService service, @Nullable Integer pid) {
        service.toggleScreenShare(pid).exceptionally(e -> {
            log.warn("Discord screen share failed", e);
            return null;
        });
    }
}
