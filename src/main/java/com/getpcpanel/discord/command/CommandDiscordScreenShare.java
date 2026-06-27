package com.getpcpanel.discord.command;

import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;

import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.commands.command.ButtonAction;
import com.getpcpanel.discord.DiscordService;
import com.getpcpanel.platform.IProcessHelper;
import com.getpcpanel.util.CdiHelper;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

/**
 * Toggle Discord screen sharing. {@link Mode#SCREEN} shares via Discord's own share picker; {@link Mode#FOCUS}
 * shares the foreground window (its PID from {@link IProcessHelper}); {@link Mode#PROCESS} shares a named app
 * (its PID resolved from the running process list). Toggling again stops the share.
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
        var pid = mode == Mode.FOCUS
                ? CdiHelper.getBean(IProcessHelper.class).foregroundPid()
                : pidForExecutable(processName.stream().filter(StringUtils::isNotBlank).findFirst().orElse(""));
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

    /** PID of a live process whose executable base name matches {@code executable} (cross-platform via ProcessHandle). */
    private static OptionalInt pidForExecutable(String executable) {
        var target = baseName(executable);
        if (target == null) {
            return OptionalInt.empty();
        }
        return ProcessHandle.allProcesses()
                .filter(p -> p.info().command().map(CommandDiscordScreenShare::baseName).map(target::equals).orElse(false))
                .mapToInt(p -> (int) p.pid())
                .findFirst();
    }

    /** Lowercased executable name without directory or extension, so a full path and a bare name compare equal. */
    @Nullable
    private static String baseName(String s) {
        if (StringUtils.isBlank(s)) {
            return null;
        }
        var name = s.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        var dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name.toLowerCase(Locale.ROOT);
    }
}
