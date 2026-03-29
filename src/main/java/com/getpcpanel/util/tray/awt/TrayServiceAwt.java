package com.getpcpanel.util.tray.awt;

import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Objects;

import javax.imageio.ImageIO;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.getpcpanel.ui.HomePageController.ShowMainEvent;
import com.getpcpanel.util.tray.awt.ConditionalOnAwtTray.OnAwtTrayCondition;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

/**
 * AWT-based system tray implementation for Windows and X11 Linux.
 * Uses java.awt.SystemTray which relies on the XEmbed protocol on Linux.
 */
@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
@ConditionalOnAwtTray
class TrayServiceAwt {
    private final Event<Object> eventPublisher;
    @ConfigProperty(name = "disable.tray", defaultValue = "false") @Setter private boolean disabled;

    @PostConstruct
    public void init() {
        if (disabled || !OnAwtTrayCondition.matches()) {
            return;
        }

        var popup = new PopupMenu();
        TrayIcon trayIcon;
        SystemTray tray;
        try {
            tray = SystemTray.getSystemTray();
            var trayIconImage = ImageIO.read(Objects.requireNonNull(TrayServiceAwt.class.getResource("/assets/32x32.png")));
            var trayIconWidth = new TrayIcon(trayIconImage).getSize().width;
            trayIcon = new TrayIcon(trayIconImage.getScaledInstance(trayIconWidth, -1, 4));
        } catch (UnsupportedOperationException e) {
            log.warn("Tray icon is not supported");
            return;
        } catch (Exception e1) {
            log.error("Unable to initialize tray icon", e1);
            return;
        }
        var exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));
        trayIcon.addMouseListener(new MouseListener() {
            @Override
            public void mouseReleased(MouseEvent e) {
            }

            @Override
            public void mousePressed(MouseEvent e) {
            }

            @Override
            public void mouseExited(MouseEvent e) {
            }

            @Override
            public void mouseEntered(MouseEvent e) {
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == 1)
                    eventPublisher.fire(new ShowMainEvent());
            }
        });
        popup.add(exitItem);
        trayIcon.setToolTip("PCPanel");
        trayIcon.setPopupMenu(popup);
        try {
            tray.add(trayIcon);
        } catch (Exception e) {
            log.error("TrayIcon could not be added.", e);
        }
    }
}
