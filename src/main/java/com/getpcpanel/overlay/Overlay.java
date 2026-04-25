package com.getpcpanel.overlay;

import java.awt.Image;
import java.awt.Toolkit;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.swing.SwingUtilities;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.IconService;
import com.getpcpanel.commands.PCPanelControlEvent;
import com.getpcpanel.commands.command.ButtonAction;
import com.getpcpanel.commands.command.DialAction;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.SaveService.SaveEvent;
import com.getpcpanel.profile.dto.OverlayPosition;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.RequiredArgsConstructor;
import one.util.streamex.StreamEx;

@ApplicationScoped
@RequiredArgsConstructor
public class Overlay {
    private final SaveService save;
    private final IconService iconService;
    private final VolumeOverlay overlay = new VolumeOverlay();

    public void updateSaveValues(@Observes SaveEvent event) {
        updateStyle(null);
        determinePosition();
    }

    private void determinePosition() {
        var window = Toolkit.getDefaultToolkit().getScreenSize();
        var x = window.width;
        var y = window.height;
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
        var b = overlay.getBounds();
        b.x = x;
        b.y = y;
        overlay.setBounds(b);
    }

    public void updateStyle(@Nullable @Observes SaveEvent event) {
        overlay.setStyles(save.get());
    }

    public void handleControl(@Observes PCPanelControlEvent event) {
        if (event.initial()) {
            return;
        }
        var vol = event.vol();
        var value = vol == null ? -1 : save.get().isOverlayUseLog() ? vol.getValue(null, 0, 1) : vol.value() / 255f;
        showDebounced(value, () -> determineIconImage(event), command -> true);
    }

    private void showDebounced(float value, Supplier<CommandAndIcon> pre, Predicate<Commands> pred) {
        if (!save.get().isOverlayEnabled()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            var cai = pre.get();
            if (hasOverlay(cai.command) && pred.test(cai.command)) {
                overlay.show(value, cai.icon);
            }
        });
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
            return new CommandAndIcon(data, iconService.getImageFrom(data, setting));
        }).orElse(CommandAndIcon.DEFAULT);
    }

    private record CommandAndIcon(Commands command, Image icon) {
        static final CommandAndIcon DEFAULT = new CommandAndIcon(Commands.EMPTY, IconService.DEFAULT);
    }
}
