package com.getpcpanel.discord.command;

import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.commands.command.ButtonAction;
import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.util.CdiHelper;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

/**
 * Toggle Discord screen sharing. {@link Mode#SCREEN} shares via Discord's own share picker; {@link Mode#PROCESS}
 * and {@link Mode#FOCUS} share a specific window directly, resolving the chosen / focused executable to its PID.
 * Toggling again stops the share.
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
        Integer pid = switch (mode) {
            case SCREEN -> null; // null PID → Discord shows its own "what to share" picker
            case FOCUS -> resolvePid(CdiHelper.getBean(ISndCtrl.class).getFocusApplication());
            case PROCESS -> resolvePid(processName.stream().filter(StringUtils::isNotBlank).findFirst().orElse(null));
        };
        if (mode != Mode.SCREEN && pid == null) {
            log.warn("Discord screen share: no running process matched {} — nothing to share", mode == Mode.FOCUS ? "the focused app" : processName);
            return;
        }
        service.toggleScreenShare(pid).exceptionally(e -> {
            log.warn("Discord screen share failed", e);
            return null;
        });
    }

    /** Maps an executable name/path to a running process' PID (matching on the lowercased exe base name). */
    @Nullable
    private Integer resolvePid(@Nullable String nameOrPath) {
        var key = baseName(nameOrPath);
        if (key == null) {
            return null;
        }
        return CdiHelper.getBean(ISndCtrl.class).getRunningApplications().stream()
                .filter(a -> a.pid() > 0) // Linux/PulseAudio reports pid 0 (no real PID) — treat as unresolved, not a bogus target
                .filter(a -> key.equals(baseName(a.file() == null ? a.name() : a.file().getName())))
                .map(ISndCtrl.RunningApplication::pid)
                .findFirst().orElse(null);
    }

    /** Lowercased executable name without directory or extension (so a path and a bare name compare equal). */
    @Nullable
    private static String baseName(@Nullable String s) {
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
