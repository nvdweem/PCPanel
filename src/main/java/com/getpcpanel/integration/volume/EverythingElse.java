package com.getpcpanel.integration.volume;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.NestedCommands;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.device.Device;
import com.getpcpanel.device.DeviceHolder;
import com.getpcpanel.integration.volume.command.CommandVolumeProcess;
import com.getpcpanel.integration.volume.command.CommandVolumeProcessMute;
import com.getpcpanel.integration.volume.platform.AudioSession;
import com.getpcpanel.integration.volume.platform.ISndCtrl;
import com.getpcpanel.profile.Profile;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import one.util.streamex.StreamEx;

/**
 * The three app-group targets, which go in an app list like any other name and are expanded when the action
 * runs, so they follow what is playing at that moment:
 * <ul>
 *   <li>"All apps without their own control": every app playing sound that no App-volume or App-mute control on a connected device's
 *       active profile names. On an App-mute button it mutes all but the apps that have their own control.</li>
 *   <li>"All apps except the focused one": every app playing sound except the one with focus, recognised the way a
 *       focus-volume dial recognises it.</li>
 *   <li>"All apps": every app playing sound.</li>
 * </ul>
 * System sounds are a target of their own and in none of them.
 */
@Unremovable
@ApplicationScoped
public class EverythingElse {
    public static final String TOKEN = "All apps without their own control";
    public static final String FOCUS_TOKEN = "All apps except the focused one";

    public static final String ALL_TOKEN = "All apps";

    @Inject DeviceHolder devices;
    @Inject ISndCtrl sndCtrl;

    /** Any app-group target: names no single app, so it has no single level. */
    public static boolean isToken(String name) {
        return StringUtils.equalsIgnoreCase(StringUtils.trim(name), TOKEN) || isFocusToken(name) || isAllToken(name);
    }

    public static boolean isFocusToken(String name) {
        return StringUtils.equalsIgnoreCase(StringUtils.trim(name), FOCUS_TOKEN);
    }

    public static boolean isAllToken(String name) {
        return StringUtils.equalsIgnoreCase(StringUtils.trim(name), ALL_TOKEN);
    }

    /** {@code names} with the app-group targets, if present, replaced by the executables of the apps they stand for. */
    public Set<String> expand(Collection<String> names) {
        var result = new LinkedHashSet<String>();
        var unclaimed = false;
        var butFocused = false;
        var everyApp = false;
        for (var name : names) {
            if (isAllToken(name)) {
                everyApp = true;
            } else if (isFocusToken(name)) {
                butFocused = true;
            } else if (isToken(name)) {
                unclaimed = true;
            } else {
                result.add(name);
            }
        }
        if (unclaimed) {
            result.addAll(unclaimed(sndCtrl.getAllSessions(), claimedNames()));
        }
        if (butFocused) {
            result.addAll(allBut(sndCtrl.getAllSessions(), sndCtrl.getFocusApplication()));
        }
        if (everyApp) {
            result.addAll(all(sndCtrl.getAllSessions()));
        }
        return result;
    }

    /** Executables of every session except system sounds. */
    static Set<String> all(Collection<AudioSession> sessions) {
        return allBut(sessions, null);
    }

    /** Executables of the sessions other than the focused app's ({@code focus}: its path or name; null: none). */
    static Set<String> allBut(Collection<AudioSession> sessions, @Nullable String focus) {
        return StreamEx.of(sessions)
                       .filter(s -> s.executable() != null && !s.isSystemSounds())
                       .remove(s -> StringUtils.isNotBlank(focus)
                               && (StringUtils.equalsIgnoreCase(s.executable().getPath(), focus) || s.matches(focus)))
                       .map(s -> s.executable().getName())
                       .toCollection(LinkedHashSet::new);
    }

    /** Executables of the sessions none of {@code claimed} names; system sounds are a target of their own. */
    static Set<String> unclaimed(Collection<AudioSession> sessions, Collection<String> claimed) {
        return StreamEx.of(sessions)
                       .filter(s -> s.executable() != null && !s.isSystemSounds())
                       .filter(s -> claimed.stream().noneMatch(s::matches))
                       .map(s -> s.executable().getName())
                       .toCollection(LinkedHashSet::new);
    }

    /** Every app named by an App-volume or App-mute action on a connected device's active profile. */
    private Set<String> claimedNames() {
        return StreamEx.of(devices.all())
                       .map(Device::currentProfile)
                       .flatMap(EverythingElse::commands)
                       .flatMap(EverythingElse::processNames)
                       .remove(EverythingElse::isToken)
                       .toSet();
    }

    private static StreamEx<Command> commands(Profile p) {
        return StreamEx.of(p.getDialData().values())
                       .append(p.getButtonData().values())
                       .append(p.getDblButtonData().values())
                       .append(p.getHoldButtonData().values())
                       .append(p.getReleaseButtonData().values())
                       .nonNull()
                       .flatMap(EverythingElse::flatten);
    }

    private static StreamEx<Command> flatten(Commands commands) {
        return StreamEx.of(commands.getCommands())
                       .flatMap(c -> c instanceof NestedCommands n ? StreamEx.of(n.nestedCommands()).flatMap(EverythingElse::flatten).prepend(c) : StreamEx.of(c));
    }

    private static StreamEx<String> processNames(Command command) {
        if (command instanceof CommandVolumeProcess p && p.getProcessName() != null) {
            return StreamEx.of(p.getProcessName());
        }
        if (command instanceof CommandVolumeProcessMute m && m.getProcessName() != null) {
            return StreamEx.of(m.getProcessName());
        }
        return StreamEx.empty();
    }
}
