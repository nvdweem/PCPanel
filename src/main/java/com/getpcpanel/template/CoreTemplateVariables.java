package com.getpcpanel.template;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.device.Device;
import com.getpcpanel.device.DeviceHolder;
import com.getpcpanel.integration.volume.mutecolor.MuteStateResolver;
import com.getpcpanel.integration.volume.platform.ISndCtrl;

import io.quarkus.arc.All;
import io.quarkus.qute.TemplateData;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/** The variables every template has, whatever integration its control drives. */
@Log4j2
@ApplicationScoped
public class CoreTemplateVariables {
    public static final Set<String> NAMES = Set.of("value", "percent", "raw", "name", "muted", "device", "profile", "control", "focusApp");

    @Inject
    DeviceHolder devices;
    @Inject
    @All
    List<MuteStateResolver> muteResolvers;
    @Inject
    Instance<ISndCtrl> sndCtrl;

    @Nullable
    Object get(String name, TemplateScope scope) {
        return switch (name) {
            case "value" -> scope.value();
            case "percent" -> scope.dial() == null ? null : Math.round(scope.dial().getValue(null, 0f, 100f));
            case "raw" -> scope.dial() == null ? null : scope.dial().value();
            case "name" -> scope.name() != null ? scope.name().get() : automaticName(scope);
            case "muted" -> muted(scope);
            case "device" -> device(scope).map(DeviceView::new).orElse(null);
            case "profile" -> device(scope).map(d -> d.currentProfile().getName()).orElse(null);
            case "control" -> device(scope).map(d -> ControlView.of(d, scope)).orElse(null);
            case "focusApp" -> focusApp();
            default -> null;
        };
    }

    private Optional<Device> device(TemplateScope scope) {
        return scope.serial() == null ? Optional.empty() : devices.getDevice(scope.serial());
    }

    @Nullable
    private static String automaticName(TemplateScope scope) {
        if (scope.commands() == null) {
            return null;
        }
        return scope.commands().getCommands().stream().map(Command::buildLabel).filter(StringUtils::isNotBlank).findFirst().orElse(null);
    }

    @Nullable
    private Boolean muted(TemplateScope scope) {
        if (scope.commands() == null) {
            return null;
        }
        for (var resolver : muteResolvers) {
            try {
                var result = resolver.resolve(scope.commands(), MuteStateResolver.FOLLOW);
                if (result.isPresent()) {
                    return result.get();
                }
            } catch (RuntimeException e) {
                log.debug("Mute resolver {} failed for a template", resolver.getClass().getSimpleName(), e);
            }
        }
        return null;
    }

    @Nullable
    private String focusApp() {
        if (!sndCtrl.isResolvable()) {
            return null;
        }
        var app = sndCtrl.get().getFocusApplication();
        return StringUtils.isBlank(app) ? null : StringUtils.removeEndIgnoreCase(new File(app).getName(), ".exe");
    }

    @TemplateData
    public static final class DeviceView {
        private final Device device;

        DeviceView(Device device) {
            this.device = device;
        }

        public String getName() {
            return device.getDisplayName();
        }

        public String getSerial() {
            return device.getSerialNumber();
        }

        public String getKind() {
            return device.descriptor().displayName();
        }

        @Override
        public String toString() {
            return getName();
        }
    }

    @TemplateData
    public record ControlView(String label, int index, String kind) {
        static ControlView of(Device device, TemplateScope scope) {
            var descriptor = device.descriptor();
            if (scope.button()) {
                var label = descriptor.digitalInputs().stream().filter(d -> d.index() == scope.control()).map(d -> d.label()).findFirst().orElse("B" + (scope.control() + 1));
                return new ControlView(label, scope.control(), "button");
            }
            var input = descriptor.analogInputs().stream().filter(a -> a.index() == scope.control()).findFirst();
            var label = input.map(a -> a.label()).orElse("K" + (scope.control() + 1));
            var kind = input.map(a -> a.kind().name().toLowerCase(java.util.Locale.ROOT)).orElse("knob");
            return new ControlView(label, scope.control(), kind);
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
