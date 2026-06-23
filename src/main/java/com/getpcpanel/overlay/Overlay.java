package com.getpcpanel.overlay;

import java.awt.Image;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.IconService;
import com.getpcpanel.commands.PCPanelControlEvent;
import com.getpcpanel.commands.command.ButtonAction;
import com.getpcpanel.commands.command.DialAction;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.SaveService.SaveEvent;
import com.getpcpanel.profile.dto.OverlayPosition;
import com.sun.jna.Platform;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class Overlay {
    private final SaveService save;
    private final IconService iconService;
    // The AWT/Swing windowing toolkit is unsupported in the GraalVM native image (it segfaults the
    // native WToolkit event loop), so neither overlay uses it: Windows draws a JNA layered window and
    // Linux/Wayland asks the desktop to draw it over D-Bus (KDE volume OSD, else a notification).
    // macOS still has no AWT-free overlay.
    private final OverlayWindow overlay = createOverlay();

    private static OverlayWindow createOverlay() {
        if (Platform.isWindows()) {
            return new Win32VolumeOverlay();
        }
        if (Platform.isLinux()) {
            return new LinuxOverlay();
        }
        return new NoOpOverlayWindow();
    }

    public void updateSaveValues(@Observes SaveEvent event) {
        updateStyle(null);
        determinePosition();
    }

    private void determinePosition() {
        var window = overlay.getScreenSize();
        var x = window.width();
        var y = window.height();
        var width = overlay.getWidth();
        var height = overlay.getHeight();

        var position = save == null ? OverlayPosition.topLeft : save.get().getOverlayPosition();
        var padding = save == null ? 0 : save.get().getOverlayPadding();
        var newY = switch (position) {
            case topLeft, topMiddle, topRight -> padding;
            case middleLeft, middleMiddle, middleRight -> y / 2 - height / 2;
            case bottomLeft, bottomMiddle, bottomRight -> y - overlay.getHeight() - padding;
        };
        var newX = switch (position) {
            case topLeft, middleLeft, bottomLeft -> padding;
            case topMiddle, middleMiddle, bottomMiddle -> x / 2 - width / 2;
            case topRight, middleRight, bottomRight -> x - width - padding;
        };
        setXY(newX, newY);
    }

    private void setXY(int x, int y) {
        overlay.setLocation(x, y);
    }

    public void show(float value) {
        showDebounced(value, () -> CommandAndIcon.DEFAULT, x -> true);
    }

    public void updateStyle(@Nullable @Observes SaveEvent event) {
        overlay.setStyles(save.get());
    }

    public void handleControl(@Observes PCPanelControlEvent event) {
        if (event.initial()) {
            return;
        }
        // The overlay is purely cosmetic, but this observer runs synchronously on the same event
        // notification as CommandDispatcher (the bean that actually performs the volume/mute/etc. action),
        // on the HID input thread. An uncaught Throwable here would abort the notification (so the command
        // never runs) and kill the input thread, freezing ALL hardware control. The icon path depends on
        // AWT/Java2D, which is fragile in the native image, so isolate it: on any failure, log and move on.
        try {
            var vol = event.vol();
            // The log/linear scale only shapes our own custom-rendered bar (Windows). On Linux the
            // desktop's native OSD shows the true volume, so the scale doesn't apply there (the setting
            // is disabled in the UI) — always report the real linear level.
            var useLog = save.get().isOverlayUseLog() && !Platform.isLinux();
            var value = vol == null ? -1 : useLog ? vol.getValue(null, 0, 1) : vol.value() / 255f;
            showDebounced(value, () -> determineIconImage(event), command -> true);
        } catch (Throwable t) {
            log.warn("Overlay failed to handle control event; ignoring (hardware control is unaffected)", t);
        }
    }

    private void showDebounced(float value, Supplier<CommandAndIcon> pre, Predicate<Commands> pred) {
        if (!save.get().isOverlayEnabled()) {
            return;
        }
        var cai = pre.get();
        if (hasOverlay(cai.command) && pred.test(cai.command)) {
            overlay.show(value, cai.icon);
        }
    }

    private boolean hasOverlay(Commands commands) {
        return Commands.hasCommands(commands) &&
                StreamEx.of(commands.getCommands()).anyMatch(command -> command instanceof DialAction da && da.hasOverlay()
                        || command instanceof ButtonAction ba && ba.hasOverlay());
    }

    @Nonnull
    private CommandAndIcon determineIconImage(PCPanelControlEvent event) {
        return save.getProfile(event.serialNum()).map(profile -> {
            var data = event.cmd();
            var setting = event.vol() == null ? null : profile.getKnobSettings(event.knob());
            // Icon decoding needs libawt (Windows only); elsewhere the overlay is a no-op that ignores
            // the icon, so skip the BufferedImage lookup entirely to stay libawt-free.
            var icon = Platform.isWindows() ? iconService.getImageFrom(data, setting) : null;
            return new CommandAndIcon(data, icon);
        }).orElse(CommandAndIcon.DEFAULT);
    }

    private record CommandAndIcon(Commands command, Image icon) {
        static final CommandAndIcon DEFAULT = new CommandAndIcon(Commands.EMPTY, null);
    }
}
