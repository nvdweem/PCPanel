package com.getpcpanel.integration.volume.mutecolor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.integration.volume.command.CommandVolumeFocus;
import com.getpcpanel.integration.volume.command.CommandVolumeFocusMute;
import com.getpcpanel.integration.volume.command.CommandVolumeProcess;
import com.getpcpanel.integration.volume.command.CommandVolumeProcessMute;
import com.getpcpanel.integration.volume.platform.AudioSession;
import com.getpcpanel.integration.volume.platform.ISndCtrl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Mute state of a per-application control, incl. "System Sounds". The applications are taken from an
 * app-volume dial ({@link CommandVolumeProcess}) or an app-mute button ({@link CommandVolumeProcessMute});
 * the focused-app commands ({@link CommandVolumeFocus}, {@link CommandVolumeFocusMute}) resolve against
 * whichever application currently has focus. The dial wins when a control has both.
 */
@ApplicationScoped
class ProcessMuteResolver implements MuteStateResolver {
    @Inject
    ISndCtrl sndCtrl;

    @Override
    public Optional<Boolean> resolve(Commands command, String target) {
        if (!FOLLOW.equals(target)) {
            return Optional.empty();
        }
        var dial = command.getCommand(CommandVolumeProcess.class);
        if (dial.isPresent()) {
            return muteForProcesses(dial.get().getProcessName());
        }
        var button = command.getCommand(CommandVolumeProcessMute.class);
        if (button.isPresent()) {
            return muteForProcesses(button.get().getProcessName());
        }
        if (command.getCommand(CommandVolumeFocus.class).isPresent() || command.getCommand(CommandVolumeFocusMute.class).isPresent()) {
            return muteForFocusedApplication();
        }
        return Optional.empty();
    }

    /** The focused-app controls act on whatever has focus, so the colour tracks that same moving target
     *  ({@code MuteColorService} recomputes on a focus change). */
    private Optional<Boolean> muteForFocusedApplication() {
        var focused = sndCtrl.getFocusApplication();
        return StringUtils.isBlank(focused) ? Optional.empty() : muteForProcesses(List.of(focused));
    }

    private Optional<Boolean> muteForProcesses(Collection<String> names) {
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

    private boolean matches(AudioSession session, Collection<String> names) {
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
