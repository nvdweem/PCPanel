package com.getpcpanel.mutecolor;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.command.CommandVolumeProcess;
import com.getpcpanel.cpp.AudioSession;
import com.getpcpanel.cpp.ISndCtrl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Mute state of a per-application volume control ({@link CommandVolumeProcess}), incl. "System Sounds". */
@ApplicationScoped
public class ProcessMuteResolver implements MuteStateResolver {
    @Inject
    ISndCtrl sndCtrl;

    @Override
    public Optional<Boolean> resolve(Commands command, String target) {
        if (!FOLLOW.equals(target)) {
            return Optional.empty();
        }
        return command.getCommand(CommandVolumeProcess.class)
                      .flatMap(cmd -> muteForProcesses(cmd.getProcessName()));
    }

    private Optional<Boolean> muteForProcesses(List<String> names) {
        if (names == null || names.isEmpty()) {
            return Optional.empty();
        }
        Boolean result = null;
        for (var session : sndCtrl.getAllSessions()) {
            if (matches(session, names)) {
                result = (result != null && result) || session.muted();
            }
        }
        return Optional.ofNullable(result);
    }

    private boolean matches(AudioSession session, List<String> names) {
        var exe = session.executable() != null ? session.executable().getName() : null;
        var title = session.title();
        for (var name : names) {
            if (StringUtils.isBlank(name)) {
                continue;
            }
            if (StringUtils.equalsIgnoreCase(title, name)) {
                return true;
            }
            if (StringUtils.isNotBlank(exe)
                    && (StringUtils.equalsIgnoreCase(exe, name) || StringUtils.containsIgnoreCase(name, exe) || StringUtils.containsIgnoreCase(exe, name))) {
                return true;
            }
            if (session.isSystemSounds() && StringUtils.containsIgnoreCase(name, "system")) {
                return true;
            }
        }
        return false;
    }
}
