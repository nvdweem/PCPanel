package com.getpcpanel.volume;

import java.io.File;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandVolumeProcess;
import com.getpcpanel.commands.command.DialAction;
import com.getpcpanel.commands.command.DialAction.DialActionParameters;
import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.hid.DialValue;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.dto.FocusVolumeOverride;
import com.getpcpanel.profile.dto.FocusVolumeTarget;
import com.getpcpanel.profile.dto.KnobSetting;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

/**
 * Applies the user's {@link FocusVolumeOverride} rules (issue #49): when the focused app matches a rule's
 * sources, the focus dial value is applied to that rule's targets instead of the focused app's own audio
 * session.
 *
 * <p>This lives at the {@link VolumeCoordinatorService} level rather than in the {@link IFocusRedirector}
 * chain because a target can be any command — including device-scoped ones (brightness, device volume) —
 * so applying it needs the originating device, which the coordinator has but the audio-only redirectors
 * (Wave Link, Linux new-session) neither have nor need.
 */
@Log4j2
@Unremovable
@ApplicationScoped
public class FocusVolumeOverrideService {
    /** Audio device selector meaning "every render device" in {@link ISndCtrl#setProcessVolume}. */
    private static final String ALL_DEVICES = "*";

    @Inject SaveService saveService;
    @Inject ISndCtrl sndCtrl;

    /**
     * Apply any override rules matching the focused {@code application}.
     *
     * @param device the serial of the PCPanel device whose focus dial fired (target commands run as if
     *               that device's dial moved, so device-scoped targets resolve correctly)
     * @return {@code true} if a rule matched and the request is fully handled (the caller must NOT also
     *         apply the normal focused-app volume); {@code false} when no rule matched
     */
    public boolean handle(String application, float volume, String device) {
        var matches = matching(application);
        if (matches.isEmpty()) {
            return false;
        }
        var includeSource = false;
        for (var rule : matches) {
            applyTargets(rule, volume, device);
            includeSource |= rule.includeSource();
        }
        // "Also control the source app": drive the focused source's own sessions across every render
        // device (apps often don't play on the default device — e.g. Steam routes to "Steam Streaming
        // Speakers"). Off by default for apps that never play audio themselves (Steam → SteamWebHelper).
        if (includeSource) {
            sndCtrl.setProcessVolume(new File(application).getName(), ALL_DEVICES, volume);
        }
        return true;
    }

    /** Whether any override rule claims {@code application}. Side-effect-free, for diagnostics. */
    public boolean controls(String application) {
        return !matching(application).isEmpty();
    }

    /** Rules whose sources include {@code application}'s exe basename (case-insensitive). */
    private List<FocusVolumeOverride> matching(String application) {
        if (StringUtils.isBlank(application)) {
            return List.of();
        }
        var basename = new File(application).getName();
        return StreamEx.of(saveService.get().getFocusVolumeOverrides())
                       .filter(o -> StreamEx.of(o.sources()).nonNull().anyMatch(s -> s.equalsIgnoreCase(basename)))
                       .toList();
    }

    /** Run each target command as if {@code device}'s dial moved to {@code volume} (0..1). */
    private void applyTargets(FocusVolumeOverride rule, float volume, String device) {
        // A null KnobSetting → the calculator passes the raw value straight through (linear, no trim), so a
        // target receives exactly the focus value and then applies its own per-command mapping (invert, etc).
        var context = new DialActionParameters(device, false, new DialValue((KnobSetting) null, Math.round(volume * 255f)));
        for (FocusVolumeTarget target : rule.targets()) {
            if (target == null || target.command() == null) {
                continue;
            }
            var command = allRenderDevicesIfUnset(target.command());
            if (command instanceof DialAction da) {
                try {
                    da.execute(context);
                } catch (RuntimeException e) {
                    log.warn("Focus override target failed: {}", command, e);
                }
            } else {
                log.debug("Focus override target is not a dial action, skipping: {}", command);
            }
        }
    }

    /**
     * An App-volume target controls its app on every render device, not just the current default. The
     * override target editor never offers a device picker, so a blank device there means "this app,
     * wherever it plays" — and many apps don't play on the default device (e.g. Steam). A device that was
     * deliberately chosen elsewhere is left as-is.
     */
    static Command allRenderDevicesIfUnset(Command command) {
        if (command instanceof CommandVolumeProcess p && StringUtils.isBlank(p.getDevice())) {
            return new CommandVolumeProcess(p.getProcessName(), ALL_DEVICES, p.isUnMuteOnVolumeChange(), p.getDialParams());
        }
        return command;
    }
}
