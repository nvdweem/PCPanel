package com.getpcpanel.integration.volume;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.integration.volume.command.CommandVolumeDevice;
import com.getpcpanel.integration.volume.command.CommandVolumeDeviceMute;
import com.getpcpanel.integration.volume.command.CommandVolumeFocus;
import com.getpcpanel.integration.volume.command.CommandVolumeFocusMute;
import com.getpcpanel.integration.volume.command.CommandVolumeProcess;
import com.getpcpanel.integration.volume.command.CommandVolumeProcessMute;
import com.getpcpanel.integration.volume.platform.AudioDevice;
import com.getpcpanel.integration.volume.platform.AudioSession;
import com.getpcpanel.integration.volume.platform.ISndCtrl;
import com.getpcpanel.template.LazyMap;
import com.getpcpanel.template.TemplateNamespace;
import com.getpcpanel.template.TemplateScope;

import io.quarkus.qute.TemplateData;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

/** {@code {{ audio.… }}}: the operating system's audio devices and application volumes. Volumes are percentages. */
@ApplicationScoped
public class AudioTemplateNamespace implements TemplateNamespace {
    @Inject
    Instance<ISndCtrl> sndCtrl;

    @Override
    public String name() {
        return "audio";
    }

    @Override
    public Class<?> rootType() {
        return Root.class;
    }

    @Override
    @Nullable
    public Object root(TemplateScope scope) {
        return sndCtrl.isResolvable() ? new Root(sndCtrl.get(), scope) : null;
    }

    static long percent(float volume) {
        return Math.round(volume * 100);
    }

    /** The key an application is reached by: its executable name without {@code .exe}, lower-cased. */
    static String appKey(AudioSession session) {
        var name = session.executable() == null ? session.title() : session.executable().getName();
        return StringUtils.removeEndIgnoreCase(StringUtils.defaultString(name), ".exe").toLowerCase(java.util.Locale.ROOT);
    }

    @TemplateData
    public static final class Root {
        private final ISndCtrl snd;
        private final TemplateScope scope;

        Root(ISndCtrl snd, TemplateScope scope) {
            this.snd = snd;
            this.scope = scope;
        }

        public LazyMap<DeviceView> getDevice() {
            var devices = snd.getDevicesMap();
            return LazyMap.of(devices, id -> new DeviceView(devices.get(id)));
        }

        @Nullable
        public DeviceView getDefaultOutput() {
            return device(snd.defaultPlayer());
        }

        @Nullable
        public DeviceView getDefaultInput() {
            return device(snd.defaultRecorder());
        }

        public LazyMap<AppView> getApp() {
            var apps = new LinkedHashMap<String, AppView>();
            for (var session : snd.getAllSessions()) {
                apps.computeIfAbsent(appKey(session), k -> new AppView(session));
            }
            return LazyMap.of(apps, apps::get);
        }

        /** What this control acts on: an application, a device, or the focused application. */
        @Nullable
        public TargetView getTarget() {
            var commands = scope.commands();
            if (commands == null) {
                return null;
            }
            var processes = commands.getCommand(CommandVolumeProcess.class).<Collection<String>>map(CommandVolumeProcess::getProcessName)
                                    .or(() -> commands.getCommand(CommandVolumeProcessMute.class).map(CommandVolumeProcessMute::getProcessName));
            if (processes.isPresent()) {
                return TargetView.of(app(processes.get()));
            }
            var deviceId = commands.getCommand(CommandVolumeDevice.class).map(CommandVolumeDevice::getDeviceId)
                                   .or(() -> commands.getCommand(CommandVolumeDeviceMute.class).map(CommandVolumeDeviceMute::getDeviceId));
            if (deviceId.isPresent()) {
                return TargetView.of(device(snd.defaultDeviceOnEmpty(deviceId.get())));
            }
            if (commands.getCommand(CommandVolumeFocus.class).isPresent() || commands.getCommand(CommandVolumeFocusMute.class).isPresent()) {
                var focus = snd.getFocusApplication();
                return StringUtils.isBlank(focus) ? null : TargetView.of(app(List.of(focus)));
            }
            return null;
        }

        @Nullable
        private DeviceView device(@Nullable String id) {
            var device = id == null ? null : snd.getDevice(id);
            return device == null ? null : new DeviceView(device);
        }

        @Nullable
        private AppView app(@Nullable Collection<String> names) {
            if (names == null) {
                return null;
            }
            for (var session : snd.getAllSessions()) {
                if (names.stream().anyMatch(n -> session.matches(n) || StringUtils.equalsIgnoreCase(new java.io.File(StringUtils.defaultString(n)).getName(), session.executable() == null ? null : session.executable().getName()))) {
                    return new AppView(session);
                }
            }
            return null;
        }
    }

    @TemplateData
    public record TargetView(String name, long volume, boolean muted) {
        @Nullable
        static TargetView of(@Nullable DeviceView device) {
            return device == null ? null : new TargetView(device.getName(), device.getVolume(), device.isMuted());
        }

        @Nullable
        static TargetView of(@Nullable AppView app) {
            return app == null ? null : new TargetView(app.getName(), app.getVolume(), app.isMuted());
        }

        @Override
        public String toString() {
            return name;
        }
    }

    @TemplateData
    public static final class DeviceView {
        private final AudioDevice device;

        DeviceView(AudioDevice device) {
            this.device = device;
        }

        public String getId() {
            return device.id();
        }

        public String getName() {
            return device.name();
        }

        public long getVolume() {
            return percent(device.volume());
        }

        public boolean isMuted() {
            return device.muted();
        }

        /** {@code output}, {@code input} or {@code both}. */
        public String getType() {
            var flow = device.dataflow();
            return flow == null ? "output" : flow.input() && flow.output() ? "both" : flow.input() ? "input" : "output";
        }

        @Override
        public String toString() {
            return getName();
        }
    }

    @TemplateData
    public static final class AppView {
        private final AudioSession session;

        AppView(AudioSession session) {
            this.session = session;
        }

        public String getName() {
            return session.title();
        }

        public long getVolume() {
            return percent(session.volume());
        }

        public boolean isMuted() {
            return session.muted();
        }

        @Override
        public String toString() {
            return getName();
        }
    }
}
