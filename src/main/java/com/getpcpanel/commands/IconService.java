package com.getpcpanel.commands;

import static com.getpcpanel.integration.volume.platform.AudioSession.SYSTEM;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiFunction;

import javax.annotation.Nonnull;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.integration.device.command.CommandBrightness;
import com.getpcpanel.integration.volume.command.CommandVolumeDefaultDevice;
import com.getpcpanel.integration.volume.command.CommandVolumeDefaultDeviceAdvanced;
import com.getpcpanel.integration.volume.command.CommandVolumeDefaultDeviceToggle;
import com.getpcpanel.integration.volume.command.CommandVolumeDefaultDeviceToggleAdvanced;
import com.getpcpanel.integration.volume.command.CommandVolumeDevice;
import com.getpcpanel.integration.volume.command.CommandVolumeFocus;
import com.getpcpanel.integration.volume.command.CommandVolumeProcess;
import com.getpcpanel.integration.volume.platform.ISndCtrl;
import com.getpcpanel.iconextract.IIconService;
import com.getpcpanel.profile.dto.KnobSetting;
import com.getpcpanel.util.PngDecoder;

import io.quarkus.arc.All;
import io.quarkus.cache.CacheResult;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@ApplicationScoped
public class IconService {
    public BufferedImage DEFAULT;
    public BufferedImage OBS;
    public BufferedImage VOICEMEETER;
    public BufferedImage DEVICE;
    public BufferedImage SYSTEM_SOUND;

    @Nullable
    private static BufferedImage loadImage(String path) {
        // Only the Windows native image bundles libawt (headless Java2D). On macOS and Linux there is no
        // libawt, so even constructing a BufferedImage fails (its Java2D class initializer throws); icons
        // are simply not shown there, so return null and never touch AWT.
        if (!SystemUtils.IS_OS_WINDOWS) {
            return null;
        }
        // On Windows, decode without ImageIO: ImageIO's initialization reaches java.awt.Toolkit (awt.dll),
        // which is dead in the native image and throws NoClassDefFoundError — which would kill the input
        // thread on the first overlay icon. PngDecoder is pure Java and never touches AWT.
        try {
            var url = IconService.class.getResource(path);
            if (url != null) {
                try (var in = url.openStream()) {
                    var img = PngDecoder.decode(in.readAllBytes());
                    if (img != null) {
                        return img;
                    }
                }
            }
        } catch (Throwable t) {
            log.trace("Unable to load image {}", path, t);
        }
        return null;
    }

    private final SafeMap imageHandlers = new SafeMap();
    @Inject
    ISndCtrl sndCtrl;
    @Inject
    IIconService iconService;
    @Inject @All
    List<IIconHandler<?>> iconHandlers;

    @PostConstruct
    public void init() {
        DEFAULT = loadImage("/assets/32x32.png");
        OBS = loadImage("/assets/obs.png");
        VOICEMEETER = loadImage("/assets/voicemeeter.png");
        DEVICE = loadImage("/assets/device.png");
        SYSTEM_SOUND = loadImage("/assets/systemsounds.ico");

        imageHandlers.put(Command.class, (a, b) -> DEFAULT);

        // Dials
        imageHandlers.put(CommandVolumeProcess.class, IconService::getRunningProcessIcon);
        imageHandlers.put(CommandVolumeFocus.class, IconService::getFocusProcessIcon);
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

    @CacheResult(cacheName = "command-icon")
    @Nonnull
    public BufferedImage getImageFrom(@Nullable Commands commands, @Nullable KnobSetting override) {
        // Icon extraction/decoding (ImageIO, JIconExtract) needs libawt, which only the Windows native
        // image bundles. On macOS and Linux short-circuit to the blank default to keep the path libawt-free.
        if (!SystemUtils.IS_OS_WINDOWS) {
            return DEFAULT;
        }
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
        if (sndCtrl.getFocusApplication() == null) {
            return DEFAULT;
        }
        var image = iconService.getIconForFile(32, 32, new File(sndCtrl.getFocusApplication()));
        if (image == null) {
            return DEFAULT;
        }
        return image;
    }

    private BufferedImage getDeviceIcon(Command command) {
        return DEVICE;
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
