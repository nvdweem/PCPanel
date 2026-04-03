package com.getpcpanel.commands;

import static com.getpcpanel.cpp.AudioSession.SYSTEM;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import javax.imageio.ImageIO;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import io.quarkus.cache.CacheResult;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;

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
import java.awt.image.BufferedImage;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@ApplicationScoped
public class IconService {
    public static final BufferedImage DEFAULT = loadImage("/assets/32x32.png");
    private static final BufferedImage OBS = loadImage("/assets/obs.png");
    private static final BufferedImage VOICEMEETER = loadImage("/assets/voicemeeter.png");
    public static final BufferedImage DEVICE = loadImage("/assets/device.png");
    public static final BufferedImage SYSTEM_SOUND = loadImage("/assets/systemsounds.ico");

    private static BufferedImage loadImage(String path) {
        try {
            var url = IconService.class.getResource(path);
            if (url != null) return ImageIO.read(url);
        } catch (IOException e) {
            // fall through
        }
        return new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
    }
    private final SafeMap imageHandlers = new SafeMap();
    @Inject
    ISndCtrl sndCtrl;
    @Inject
    IIconService iconService;
    private final List<IIconHandler<?>> iconHandlers;

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

        // Other handlers
        iconHandlers.forEach(ih -> {
            IIconHandler untyped = ih;
            imageHandlers.put(ih.getCommandClass(), (is, cmd) -> (BufferedImage) untyped.supplyImage(cmd).orElse(null));
        });
    }

    @CacheResult(cacheName="command-icon")
    @Nonnull
    public BufferedImage getImageFrom(@Nullable Commands commands, @Nullable KnobSetting override) {
        if (!Commands.hasCommands(commands)) {
            return DEFAULT;
        }

        if (override != null) {
            try {
                var iconStr = override.getOverlayIcon();
                if (StringUtils.endsWithAny(iconStr, "exe", "dll") && new File(iconStr).exists()) {
                    var result = iconService.getIconForFile(32, 32, new File(iconStr));
                    if (result != null) {
                        return result;
                    }
                }
                if (StringUtils.isNotBlank(iconStr)) {
                    return loadImage(iconStr);
                }
            } catch (Exception e) {
                log.trace("Unable to load {}", override, e);
            }
        }

        //noinspection ObjectEquality
        return StreamEx.of(commands.getCommands())
                       .map(imageHandlers::handle)
                       .findFirst(result -> result != null && result != DEFAULT)
                       .orElse(DEFAULT);
    }

    public boolean isDefault(BufferedImage img) {
        //noinspection ObjectEquality
        return img == DEFAULT;
    }

    private BufferedImage getRunningProcessIcon(CommandVolumeProcess commandIcon) {
        var allProcesses = sndCtrl.getRunningApplications();
        for (var process : commandIcon.getProcessName()) {
            if (StringUtils.equalsIgnoreCase(process, SYSTEM)) {
                return SYSTEM_SOUND;
            }
            for (var runningProcess : allProcesses) {
                if (StringUtils.containsIgnoreCase(runningProcess.file().getAbsolutePath(), process)) {
                    var image = iconService.getIconForFile(32, 32, runningProcess.file());
                    if (image != null) {
                        return image;
                    }
                }
            }
        }
        return DEFAULT;
    }

    private BufferedImage getFocusProcessIcon(CommandVolumeFocus command) {
        var image = iconService.getIconForFile(32, 32, new File(sndCtrl.getFocusApplication()));
        if (image == null) {
            return DEFAULT;
        }
        return image;
    }

    private BufferedImage getDeviceIcon(Command command) {
        return DEVICE;
    }

    private BufferedImage getVoiceMeeterIcon(CommandVoiceMeeter command) {
        return VOICEMEETER;
    }

    private BufferedImage getObsIcon(CommandObs command) {
        return OBS;
    }

    private class SafeMap extends HashMap<Class<? extends Command>, BiFunction<IconService, ? extends Command, BufferedImage>> {

        public <T extends Command> void put(Class<T> key, BiFunction<IconService, T, BufferedImage> value) {
            super.put(key, value);
        }

        public <T extends Command> BufferedImage handle(T icon) {
            if (icon == null)
                return DEFAULT;

            //noinspection unchecked
            return ((BiFunction<IconService, T, BufferedImage>) ensureHandler(icon.getClass())).apply(IconService.this, icon);
        }

        @SuppressWarnings("unchecked")
        private <T> BiFunction<IconService, T, BufferedImage> ensureHandler(Class<T> icon) {
            if (imageHandlers.containsKey(icon)) {
                return (BiFunction<IconService, T, BufferedImage>) imageHandlers.get(icon);
            }

            var handler = (BiFunction<IconService, T, BufferedImage>) ensureHandler(icon.getSuperclass());
            imageHandlers.put((Class<? extends Command>) icon, (BiFunction<IconService, ? extends Command, BufferedImage>) handler);
            return handler;
        }
    }

    private BufferedImage getBrightnessIcon(Command command) {
        return DEFAULT;
    }
}
