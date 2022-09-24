package com.getpcpanel.commands;

import java.io.File;
import java.util.HashMap;
import java.util.Objects;
import java.util.function.BiFunction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandVolumeFocus;
import com.getpcpanel.commands.command.CommandVolumeProcess;
import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.iconextract.IIconService;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
public class IconService {
    static final Image DEFAULT = new Image(Objects.requireNonNull(IconService.class.getResource("/assets/32x32.png")).toExternalForm());
    private final SafeMap imageHandlers = new SafeMap();
    private final ISndCtrl sndCtrl;
    private final IIconService iconService;

    @PostConstruct
    public void init() {
        imageHandlers.put(CommandVolumeProcess.class, IconService::getRunningProcessIcon);
        imageHandlers.put(CommandVolumeFocus.class, IconService::getFocusProcessIcon);
    }

    @Cacheable("command-image")
    public @Nonnull Image getImageFrom(@Nullable Command command, @Nullable String override) {
        if (command == null) {
            return DEFAULT;
        }

        if (StringUtils.isNotBlank(override)) {
            try {
                return new Image(override);
            } catch (Exception e) {
                log.trace("Unable to load {}", override, e);
            }
        }

        return imageHandlers.handle(command);
    }

    private Image getRunningProcessIcon(CommandVolumeProcess commandIcon) {
        var allProcesses = sndCtrl.getRunningApplications();
        for (var process : commandIcon.getProcessName()) {
            for (var runningProcess : allProcesses) {
                if (StringUtils.containsIgnoreCase(runningProcess.file().getAbsolutePath(), process)) {
                    var image = iconService.getIconForFile(32, 32, runningProcess.file());
                    if (image != null) {
                        return SwingFXUtils.toFXImage(image, null);
                    }
                }
            }
        }
        return DEFAULT;
    }

    private Image getFocusProcessIcon(CommandVolumeFocus command) {
        var image = iconService.getIconForFile(32, 32, new File(sndCtrl.getFocusApplication()));
        if (image == null) {
            return DEFAULT;
        }
        return SwingFXUtils.toFXImage(image, null);
    }

    private class SafeMap extends HashMap<Class<? extends Command>, BiFunction<IconService, ? extends Command, Image>> {
        public <T extends Command> void put(Class<T> key, BiFunction<IconService, T, Image> value) {
            super.put(key, value);
        }

        public <T extends Command> Image handle(T icon) {
            if (icon == null)
                return DEFAULT;

            @SuppressWarnings("unchecked")
            var fnc = (BiFunction<IconService, T, Image>) imageHandlers.computeIfAbsent(icon.getClass(), ignored -> (a, b) -> DEFAULT);
            return fnc.apply(IconService.this, icon);
        }
    }
}
