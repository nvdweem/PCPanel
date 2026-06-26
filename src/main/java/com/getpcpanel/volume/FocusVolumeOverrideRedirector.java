package com.getpcpanel.volume;

import java.io.File;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.commands.command.DialAction;
import com.getpcpanel.commands.command.DialAction.DialActionParameters;
import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.hid.DialValue;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.dto.KnobSetting;
import com.getpcpanel.profile.dto.FocusVolumeOverride;
import com.getpcpanel.profile.dto.FocusVolumeTarget;

import io.quarkus.arc.Unremovable;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

/**
 * Redirects focus volume according to the user's {@link FocusVolumeOverride} rules (issue #49). When the
 * focused app matches a rule's sources, the focus dial value is applied to every target in that rule
 * instead of (or, with {@code includeSource}, alongside) the focused app's own audio session.
 *
 * <p>Registered with a high {@link Priority} so it is evaluated before the Wave Link redirector: an
 * explicit override wins over Wave Link's implicit focus-app-to-channel routing.
 */
@Log4j2
@Unremovable
@Priority(1000)
@ApplicationScoped
public class FocusVolumeOverrideRedirector implements IFocusRedirector {
    @Inject SaveService saveService;
    @Inject ISndCtrl sndCtrl;

    @Override
    public boolean handleFocusVolumeRequest(String targetProcess, float volume) {
        var matches = matching(targetProcess);
        if (matches.isEmpty()) {
            return false;
        }
        var includeSource = false;
        for (var rule : matches) {
            applyTargets(rule, volume);
            includeSource |= rule.includeSource();
        }
        // includeSource: also drive the source app's own OS volume. Otherwise (e.g. steam.exe, which only
        // ever spawns a helper that plays audio) the source is left untouched — we claimed the request, so
        // VolumeCoordinatorService won't apply OS volume to it either.
        if (includeSource) {
            sndCtrl.setFocusVolume(volume);
        }
        return true;
    }

    @Override
    public boolean controlsFocusApp(String targetProcess) {
        return !matching(targetProcess).isEmpty();
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

    /** Run each target command as if a dial moved to {@code volume} (0..1). */
    private void applyTargets(FocusVolumeOverride rule, float volume) {
        // A null KnobSetting → the calculator passes the raw value straight through (linear, no trim), so a
        // target receives exactly the focus value and then applies its own per-command mapping (invert, etc).
        var context = new DialActionParameters("", false, new DialValue((KnobSetting) null, Math.round(volume * 255f)));
        for (FocusVolumeTarget target : rule.targets()) {
            if (target == null || target.command() == null) {
                continue;
            }
            if (target.command() instanceof DialAction da) {
                try {
                    da.execute(context);
                } catch (RuntimeException e) {
                    log.warn("Focus override target failed: {}", target.command(), e);
                }
            } else {
                log.debug("Focus override target is not a dial action, skipping: {}", target.command());
            }
        }
    }
}
