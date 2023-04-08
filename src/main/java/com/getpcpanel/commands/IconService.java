package com.getpcpanel.commands;

import static com.getpcpanel.cpp.AudioSession.SYSTEM;

import java.io.File;
import java.util.HashMap;
import java.util.Objects;
import java.util.function.BiFunction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandBrightness;
import com.getpcpanel.commands.command.CommandObs;
import com.getpcpanel.commands.command.CommandVoiceMeeter;
import com.getpcpanel.commands.command.CommandVolumeDefaultDevice;
import com.getpcpanel.commands.command.CommandVolumeDefaultDeviceAdvanced;
import com.getpcpanel.commands.command.CommandVolumeDefaultDeviceToggle;
import com.getpcpanel.commands.command.CommandVolumeDefaultDeviceToggleAdvanced;
import com.getpcpanel.commands.command.CommandVolumeDevice;
import com.getpcpanel.commands.command.CommandVolumeFocus;
import com.getpcpanel.commands.command.CommandVolumeProcess;
import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.iconextract.IIconService;
import com.getpcpanel.profile.KnobSetting;
import com.getpcpanel.util.Images;

import jakarta.annotation.PostConstruct;
import javafx.scene.image.Image;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Service
@RequiredArgsConstructor
public class IconService {
    public static final Image DEFAULT = new Image(Objects.requireNonNull(IconService.class.getResource("/assets/32x32.png")).toExternalForm());
    private static final Image OBS = new Image(Objects.requireNonNull(IconService.class.getResource("/assets/obs.png")).toExternalForm());
    private static final Image VOICEMEETER = new Image(Objects.requireNonNull(IconService.class.getResource("/assets/voicemeeter.png")).toExternalForm());
    private static final Image DEVICE = new Image(Objects.requireNonNull(IconService.class.getResource("/assets/device.png")).toExternalForm());
    private static final Image SYSTEM_SOUND = new Image(Objects.requireNonNull(IconService.class.getResource("/assets/systemsounds.ico")).toExternalForm());
    private final SafeMap imageHandlers = new SafeMap();
    private final ISndCtrl sndCtrl;
    private final IIconService iconService;

    @PostConstruct
    public void init() {
        imageHandlers.put(Command.class, (a, b) -> DEFAULT);

        // Dials
        imageHandlers.put(CommandVolumeProcess.class, IconService::getRunningProcessIcon);
        imageHandlers.put(CommandVolumeFocus.class, IconService::getFocusProcessIcon);
        imageHandlers.put(CommandObs.class, IconService::getObsIcon);
        imageHandlers.put(CommandVoiceMeeter.class, IconService::getVoiceMeeterIcon);
        imageHandlers.put(CommandVolumeDevice.class, IconService::getDeviceIcon);
        imageHandlers.put(CommandBrightness.class, IconService::getBrightnessIcon);

        // Buttons
        imageHandlers.put(CommandVolumeDefaultDeviceAdvanced.class, IconService::getDeviceIcon);
        imageHandlers.put(CommandVolumeDefaultDeviceToggleAdvanced.class, IconService::getDeviceIcon);
        imageHandlers.put(CommandVolumeDefaultDevice.class, IconService::getDeviceIcon);
        imageHandlers.put(CommandVolumeDefaultDeviceToggle.class, IconService::getDeviceIcon);
    }

    @Cacheable("command-icon")
    public @Nonnull Image getImageFrom(@Nullable Commands commands, @Nullable KnobSetting override) {
        if (!Commands.hasCommands(commands)) {
            return DEFAULT;
        }

        if (override != null) {
            try {
                var iconStr = override.getOverlayIcon();
                if (StringUtils.endsWithAny(iconStr, "exe", "dll") && new File(iconStr).exists()) {
                    var result = iconService.getIconImageForFile(32, 32, new File(iconStr));
                    if (result != null) {
                        return result;
                    }
                }
                if (StringUtils.isNotBlank(iconStr)) {
                    return new Image(override.getOverlayIcon());
                }
            } catch (Exception e) {
                log.trace("Unable to load {}", override, e);
            }
        }

        return StreamEx.of(commands.getCommands())
                       .map(imageHandlers::handle)
                       .findFirst(result -> result != null && result != DEFAULT)
                       .orElse(DEFAULT);
    }

    public boolean isDefault(Image img) {
        //noinspection ObjectEquality
        return img == DEFAULT;
    }

    private Image getRunningProcessIcon(CommandVolumeProcess commandIcon) {
        var allProcesses = sndCtrl.getRunningApplications();
        for (var process : commandIcon.getProcessName()) {
            if (StringUtils.equalsIgnoreCase(process, SYSTEM)) {
                return SYSTEM_SOUND;
            }
            for (var runningProcess : allProcesses) {
                if (StringUtils.containsIgnoreCase(runningProcess.file().getAbsolutePath(), process)) {
                    var image = iconService.getIconImageForFile(32, 32, runningProcess.file());
                    if (image != null) {
                        return image;
                    }
                }
            }
        }
        return DEFAULT;
    }

    private Image getFocusProcessIcon(CommandVolumeFocus command) {
        var image = iconService.getIconImageForFile(32, 32, new File(sndCtrl.getFocusApplication()));
        if (image == null) {
            return DEFAULT;
        }
        return image;
    }

    private Image getDeviceIcon(Command command) {
        return DEVICE;
    }

    private Image getVoiceMeeterIcon(CommandVoiceMeeter command) {
        return VOICEMEETER;
    }

    private Image getObsIcon(CommandObs command) {
        return OBS;
    }

    private class SafeMap extends HashMap<Class<? extends Command>, BiFunction<IconService, ? extends Command, Image>> {

        public <T extends Command> void put(Class<T> key, BiFunction<IconService, T, Image> value) {
            super.put(key, value);
        }

        public <T extends Command> Image handle(T icon) {
            if (icon == null)
                return DEFAULT;

            //noinspection unchecked
            return ((BiFunction<IconService, T, Image>) ensureHandler(icon.getClass())).apply(IconService.this, icon);
        }

        @SuppressWarnings("unchecked")
        private <T> BiFunction<IconService, T, Image> ensureHandler(Class<T> icon) {
            if (imageHandlers.containsKey(icon)) {
                return (BiFunction<IconService, T, Image>) imageHandlers.get(icon);
            }

            var handler = (BiFunction<IconService, T, Image>) ensureHandler(icon.getSuperclass());
            imageHandlers.put((Class<? extends Command>) icon, (BiFunction<IconService, ? extends Command, Image>) handler);
            return handler;
        }
    }

    private Image getBrightnessIcon(Command command) {
        return Images.lighting;
    }
}
