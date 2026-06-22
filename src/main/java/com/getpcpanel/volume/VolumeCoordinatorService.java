package com.getpcpanel.volume;

import java.io.File;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandVolumeProcess;
import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.profile.SaveService;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Unremovable
@ApplicationScoped
public class VolumeCoordinatorService {
    @Inject ISndCtrl sndCtrl;
    @Inject Instance<IFocusRedirector> focusRedirectors;
    @Inject SaveService saveService;

    public void setFocusVolume(double value) {
        var application = sndCtrl.getFocusApplication();
        var floatValue = (float) value;

        // A redirector (e.g. Wave Link with focus-control on) gets first claim on the focused app.
        var handler = StreamEx.of(focusRedirectors.handlesStream())
                              .findFirst(fr -> fr.get().handleFocusVolumeRequest(application, floatValue));
        if (handler.isPresent()) {
            log.trace("Focus volume request for {} handled by {}", application, handler.get().get().getClass().getSimpleName());
            return;
        }
        // Optionally leave apps already controlled elsewhere (a per-app command, or Wave Link) untouched.
        if (saveService.get().isSkipControlledFocusApps() && isOtherwiseControlled(application)) {
            log.trace("Focus volume left {} untouched: already controlled elsewhere", application);
            return;
        }
        sndCtrl.setFocusVolume(floatValue);
    }

    /**
     * Whether {@code application} is already controlled by something other than the focus-volume dial:
     * an App-volume command bound to any control in an active profile, or a Wave Link channel.
     */
    private boolean isOtherwiseControlled(String application) {
        if (StringUtils.isBlank(application)) {
            return false;
        }
        var basename = new File(application).getName();
        if (controlledByProcessCommand(basename)) {
            return true;
        }
        return StreamEx.of(focusRedirectors.handlesStream()).anyMatch(fr -> fr.get().managesFocusApp(application));
    }

    /** Whether an App-volume command (the continuous per-app volume dial) on any active-profile control
     *  targets {@code basename}. App-mute is deliberately excluded: it's a button toggle with no
     *  continuous volume state for focus volume to drift out of sync with. */
    private boolean controlledByProcessCommand(String basename) {
        return StreamEx.of(saveService.get().getDevices().values())
                       .map(ds -> ds.getProfile(ds.getCurrentProfileName()).orElse(null))
                       .nonNull()
                       .flatMap(VolumeCoordinatorService::allCommands)
                       .anyMatch(cmd -> targetsProcess(cmd, basename));
    }

    private static StreamEx<Command> allCommands(Profile p) {
        return StreamEx.of(p.getDialData().values())
                       .append(p.getButtonData().values())
                       .append(p.getDblButtonData().values())
                       .append(p.getReleaseButtonData().values())
                       .nonNull()
                       .flatMap(c -> StreamEx.of(c.getCommands()));
    }

    private static boolean targetsProcess(Command cmd, String basename) {
        // App-volume only — App-mute (CommandVolumeProcessMute) is a button toggle, not a continuous
        // volume, so focus volume can't desync it and must not be blocked by it.
        if (!(cmd instanceof CommandVolumeProcess p)) {
            return false;
        }
        return StreamEx.of(p.getProcessName()).nonNull().anyMatch(n -> n.equalsIgnoreCase(basename));
    }

    /**
     * The redirector that would claim {@code application}'s focus volume, or empty when none would (the
     * OS controls it directly). Side-effect-free — evaluates the deferral decision without changing any
     * volume. Used by the dev test harness to inspect where focused-app volume goes.
     */
    public Optional<String> focusVolumeTarget(String application) {
        return StreamEx.of(focusRedirectors.handlesStream())
                .findFirst(fr -> fr.get().controlsFocusApp(application))
                .map(fr -> fr.get().getClass().getSimpleName());
    }

    /**
     * Whether focus volume would leave {@code application} untouched because the "skip otherwise
     * controlled" option is on, no redirector claims it, and it is already controlled elsewhere.
     * Side-effect-free — for the dev test harness.
     */
    public boolean wouldSkipFocusVolume(String application) {
        return saveService.get().isSkipControlledFocusApps()
                && focusVolumeTarget(application).isEmpty()
                && isOtherwiseControlled(application);
    }
}
