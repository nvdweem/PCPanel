package com.getpcpanel.cpp.linux.pulseaudio;

import static com.getpcpanel.cpp.linux.pulseaudio.PulseAudioWrapper.volumeFtoI;
import static com.getpcpanel.cpp.linux.pulseaudio.PulseAudioWrapper.volumeItoF;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import javax.annotation.concurrent.GuardedBy;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import javax.annotation.Nullable;

import com.getpcpanel.cpp.AudioDevice;
import com.getpcpanel.cpp.AudioSession;
import com.getpcpanel.cpp.AudioSessionEvent;
import com.getpcpanel.cpp.EventType;
import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.cpp.MuteType;
import com.getpcpanel.cpp.linux.LinuxProcessHelper;
import com.getpcpanel.cpp.linux.LinuxProcessHelper.ActiveWindow;
import com.getpcpanel.cpp.linux.pulseaudio.PulseAudioEventListener.LinuxDeviceChangedEvent;
import com.getpcpanel.cpp.linux.pulseaudio.PulseAudioEventListener.LinuxSessionChangedEvent;
import com.getpcpanel.cpp.linux.pulseaudio.PulseAudioWrapper.InOutput;
import com.getpcpanel.cpp.linux.pulseaudio.PulseAudioWrapper.PulseAudioTarget;
import com.getpcpanel.platform.LinuxBuild;

import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@ApplicationScoped
@LinuxBuild
public class SndCtrlPulseAudio implements ISndCtrl {
    public static final String INPUT_PREFIX = "in_";
    @Inject PulseAudioWrapper cmd;
    @Inject LinuxProcessHelper processHelper;
    @Inject Event<Object> eventBus;
    @GuardedBy("devices") private final Map<String, PulseAudioAudioDevice> devices = new HashMap<>();
    @GuardedBy("sessions") private final Set<PulseAudioAudioSession> sessions = new HashSet<>();

    @PostConstruct
    public void init() {
        initDevices(null);
        synchronized (sessions) {
            sessions.addAll(getSessionsFromCmd());
        }
    }

    public void onStart(@Observes StartupEvent ev) {
        Set<PulseAudioAudioSession> snapshot;
        synchronized (sessions) {
            snapshot = new HashSet<>(sessions);
        }
        snapshot.stream()
                .map(sess -> new AudioSessionEvent(sess, EventType.ADDED))
                .forEach(e -> eventBus.fire(e));
    }

    public void initDevices(@Observes @Nullable LinuxDeviceChangedEvent event) {
        synchronized (devices) {
            devices.clear();
            StreamEx.of(getDevicesFromCmd()).mapToEntry(AudioDevice::id, Function.identity()).into(devices);
        }
    }

    public void initSessions(@Observes @Nullable LinuxSessionChangedEvent event) {
        synchronized (sessions) {
            var prevByIndex = StreamEx.of(sessions).mapToEntry(PulseAudioAudioSession::index).invert().toMap();
            sessions.clear();
            sessions.addAll(getSessionsFromCmd());
            var currByIndex = StreamEx.of(sessions).mapToEntry(PulseAudioAudioSession::index).invert().toMap();

            // Trigger events
            var removed = StreamEx.of(prevByIndex.values()).remove(sessions::contains);
            var added = StreamEx.of(sessions).remove(prevByIndex.values()::contains);
            var changed = getChangedStream(event, prevByIndex, currByIndex);

            added.map(sess -> new AudioSessionEvent(sess, EventType.ADDED))
                 .append(removed.map(sess -> new AudioSessionEvent(sess, EventType.REMOVED)))
                 .append(changed.map(sess -> new AudioSessionEvent(sess, EventType.CHANGED)))
                 .forEach(e -> eventBus.fire(e));
        }
    }

    private StreamEx<PulseAudioAudioSession> getChangedStream(@Nullable LinuxSessionChangedEvent event, Map<Integer, PulseAudioAudioSession> prevs, Map<Integer, PulseAudioAudioSession> currents) {
        if (event == null || event.sessionId() == null) {
            return StreamEx.empty();
        }
        var prev = prevs.get(event.sessionId());
        var current = currents.get(event.sessionId());
        if (prev == null || current == null || (volumeFtoI(prev.volume()) == volumeFtoI(current.volume()) && prev.muted() == current.muted())) {
            return StreamEx.empty();
        }
        return StreamEx.of(current);
    }

    @Override
    public Map<String, AudioDevice> getDevicesMap() {
        synchronized (devices) {
            return new HashMap<>(devices);
        }
    }

    @Override
    public Collection<AudioDevice> devices() {
        synchronized (devices) {
            return StreamEx.ofValues(devices).select(AudioDevice.class).toSet();
        }
    }

    @Override
    public Collection<AudioSession> getAllSessions() {
        synchronized (sessions) {
            return allSessions().select(AudioSession.class).toSet();
        }
    }

    @Override
    public AudioDevice getDevice(String id) {
        synchronized (devices) {
            return devices.get(id);
        }
    }

    @Override
    public void setDeviceVolume(String deviceId, float volume) {
        cmd.setDeviceVolume(isOutput(deviceId), deviceIdx(deviceId), volume);
    }

    @Override
    public void muteDevice(String deviceId, MuteType mute) {
        cmd.muteDevice(isOutput(deviceId), deviceIdx(deviceId), mute);
    }

    @Override
    public void setDefaultDevice(String deviceId) {
        PulseAudioAudioDevice device;
        synchronized (devices) {
            device = devices.get(deviceId);
        }
        if (device == null)
            return;
        cmd.setDefaultDevice(isOutput(deviceId), device.index());
    }

    @Override
    public void setProcessVolume(String fileName, @Nullable String device, float volume) {
        Set<PulseAudioAudioSession> todo;
        synchronized (sessions) {
            todo = allSessions().filter(s -> matches(s, fileName)).toSet();
        }
        todo.forEach(s -> {
            s.setVolumeNoTrigger(volume); // Prevent sending the volume when 'force volume' is enabled
            cmd.setSessionVolume(s.index(), volume);
        });
    }

    @Override
    public void setFocusVolume(float volume) {
        var window = processHelper.getActiveWindow().orElse(null);
        if (window == null) {
            log.debug("Focus volume: no active window resolved (no window tool available, or nothing focused)");
            return;
        }
        Set<PulseAudioAudioSession> todo;
        synchronized (sessions) {
            todo = allSessions().filter(s -> matchesWindow(s, window)).toSet();
        }
        if (log.isDebugEnabled()) {
            logFocusMatch(window, todo);
        }
        todo.forEach(s -> {
            s.setVolumeNoTrigger(volume); // Prevent sending the volume when 'force volume' is enabled
            cmd.setSessionVolume(s.index(), volume);
        });
    }

    /**
     * Logs both sides of the focus-volume match so a "the knob does nothing" report (#88) is debuggable
     * from a single log: the identifiers resolved for the focused window, and every candidate stream with
     * its match keys (pid / portal app id / title / executable) and whether it matched. Enable with
     * {@code quarkus.log.category."com.getpcpanel".level=DEBUG} (or the env var
     * {@code QUARKUS_LOG_CATEGORY__COM_GETPCPANEL__LEVEL=DEBUG}).
     */
    private void logFocusMatch(ActiveWindow window, Set<PulseAudioAudioSession> matched) {
        Set<PulseAudioAudioSession> all;
        synchronized (sessions) {
            all = new HashSet<>(sessions);
        }
        log.debug("Focus volume: window pid={} process={} flatpakAppId={} windowClass={} windowName={}; match identifiers={}",
                window.pid(), window.process(), window.flatpakAppId(), window.windowClass(), window.windowName(), window.identifiers());
        for (var s : all) {
            log.debug("  candidate stream idx={} pid={} portalAppId={} title={} executable={} -> {}",
                    s.index(), s.pid(), s.portalAppId(), s.title(), s.executable().getName(),
                    matched.contains(s) ? "MATCH" : "no match");
        }
        if (matched.isEmpty()) {
            log.debug("Focus volume: NO stream matched the focused window (pid={}) - nothing controlled; see candidates above", window.pid());
        }
    }

    /**
     * A stream belongs to the focused window if its producing process is the window's process, or one of the
     * window's identifiers matches its name. The pid check is the robust path for Proton/Wine games, where the
     * name never lines up: the window's process is reported as {@code MainThrd} and the stream's binary as
     * {@code wine64-preloader}, yet the stream's {@code application.process.id} equals the window pid (#96). The
     * name check ({@link #matchesAny}) remains the fallback for cases where the two pids live in different
     * namespaces. Both pids must be real (>0) so two metadata-less entries can't collide on a sentinel.
     */
    static boolean matchesWindow(PulseAudioAudioSession s, ActiveWindow window) {
        return (s.pid() > 0 && s.pid() == window.pid()) || matchesAny(s, window.identifiers());
    }

    @Override
    public void muteProcesses(Set<String> fileName, MuteType mute) {
        Set<PulseAudioAudioSession> todo;
        synchronized (sessions) {
            todo = allSessions().filter(s -> matchesAny(s, fileName)).toSet();
        }
        todo.forEach(s -> cmd.muteSession(s.index(), mute));
    }

    /**
     * A session matches a binding query against its executable name, its title, or its portal app id (#88, #92).
     * A trailing {@code .exe} is ignored on both sides: Proton/Wine streams are reported as {@code <game>.exe}
     * while the focused window's title/class is just {@code <game>} (e.g. window "Deadlock" vs stream
     * "deadlock.exe"), so dropping the suffix lets focus volume bind them (#96). The portal app id is never an
     * executable, so it is matched verbatim.
     */
    static boolean matches(PulseAudioAudioSession s, @Nullable String query) {
        if (StringUtils.isBlank(query)) {
            return false;
        }
        var normalizedQuery = stripExe(query);
        return (StringUtils.isNotBlank(normalizedQuery)
                && StringUtils.equalsAnyIgnoreCase(normalizedQuery, stripExe(s.executable().getName()), stripExe(s.title())))
                || StringUtils.equalsIgnoreCase(query, s.portalAppId());
    }

    private static String stripExe(@Nullable String value) {
        return StringUtils.removeEndIgnoreCase(StringUtils.trimToEmpty(value), ".exe");
    }

    private static boolean matchesAny(PulseAudioAudioSession s, Collection<String> queries) {
        return StreamEx.of(queries).anyMatch(q -> matches(s, q));
    }

    @Override
    public @Nullable String getFocusApplication() {
        return processHelper.getActiveWindow().map(ActiveWindow::primaryIdentifier).orElse(null);
    }

    @Override
    public List<RunningApplication> getRunningApplications() {
        synchronized (sessions) {
            return allSessions().map(s -> new RunningApplication(0, s.executable(), runningAppName(s))).toList();
        }
    }

    /**
     * The selector display name doubles as the binding query, so it must be non-blank and something {@link #matches}
     * accepts (executable name / title / portal app id). Flatpak/metadata-sparse sink-inputs (e.g. Discord Canary,
     * Spotify) carry no application.process.binary, so the executable defaults to "/" and its name is blank - fall back
     * to the session title (application.name/portal id/media.name) so the app both shows a name in the selector and can
     * actually be bound (#71, #92).
     */
    static String runningAppName(PulseAudioAudioSession s) {
        return StringUtils.firstNonBlank(s.executable().getName(), s.title(), s.portalAppId());
    }

    @Override
    public @Nullable String defaultDeviceOnEmpty(String deviceId) {
        return null;
    }

    @Override
    public @Nullable String defaultPlayer() {
        synchronized (devices) {
            return StreamEx.ofValues(devices).findFirst(PulseAudioAudioDevice::isDefaultOutput).map(AudioDevice::id).orElse(null);
        }
    }

    @Override
    public @Nullable String defaultRecorder() {
        return null;
    }

    private Set<PulseAudioAudioDevice> getDevicesFromCmd() {
        return StreamEx.of(cmd.devices()).mapPartial(this::toDevice).toSet();
    }

    private Optional<PulseAudioAudioDevice> toDevice(PulseAudioTarget pa) {
        var isOutput = pa.type() == InOutput.output;
        var name = pa.metas().get("Name");
        if (StringUtils.isBlank(name)) {
            return Optional.empty();
        }
        return Optional.of(new PulseAudioAudioDevice(eventBus, pa.index(), pa.metas().get("Description"), (isOutput ? "" : INPUT_PREFIX) + pa.metas().get("Name"), pa.isDefault(), isOutput));
    }

    private Set<PulseAudioAudioSession> getSessionsFromCmd() {
        return StreamEx.of(cmd.getSessions()).map(this::toSession).toSet();
    }

    PulseAudioAudioSession toSession(PulseAudioTarget pa) {
        var props = pa.properties();
        var portalAppId = StringUtils.trimToNull(props.get("pipewire.access.portal.app_id"));
        // Some streams (notably Flatpak Spotify >= 1.2.86) expose no application.name / process.binary at all -
        // fall back to the portal app id, then media.name, so the session keeps a usable, bindable title (#92).
        var title = StringUtils.firstNonBlank(props.get("application.name"), portalAppId, props.get("media.name"));
        return new PulseAudioAudioSession(eventBus,
                pa.index(),
                NumberUtils.toInt(props.get("application.process.id"), -1),
                new File(props.getOrDefault("application.process.binary", "/")),
                title,
                "", extractVolume(pa), false,
                portalAppId);
    }

    float extractVolume(PulseAudioTarget pa) {
        var volumeStr = pa.metas().getOrDefault("Volume", "mono: 0 / 0% / -inf dB");
        var outputParts = volumeStr.split(":", 2);
        if (outputParts.length < 2) {
            return 0;
        }
        var volumeParts = outputParts[1].split("/");
        if (volumeParts.length == 0) {
            return 0;
        }
        return volumeItoF(NumberUtils.toInt(StringUtils.trimToEmpty(volumeParts[0])));
    }

    private int deviceIdx(String deviceId) {
        if (StringUtils.isBlank(deviceId)) {
            return PulseAudioWrapper.DEFAULT_DEVICE;
        }
        synchronized (devices) {
            return Optional.ofNullable(devices.get(deviceId)).map(PulseAudioAudioDevice::index).orElse(PulseAudioWrapper.NO_OP_IDX);
        }
    }

    private static boolean isOutput(String deviceId) {
        return !StringUtils.startsWith(deviceId, INPUT_PREFIX);
    }

    private StreamEx<PulseAudioAudioSession> allSessions() {
        synchronized (sessions) {
            var sessionsCopy = new HashSet<>(sessions);
            return StreamEx.of(sessionsCopy).distinct();
        }
    }
}
