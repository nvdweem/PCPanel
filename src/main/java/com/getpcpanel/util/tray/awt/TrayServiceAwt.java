package com.getpcpanel.util.tray.awt;

import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Objects;

import javax.imageio.ImageIO;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.getpcpanel.ui.HomePage;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * AWT-based system tray implementation for Windows and X11 Linux.
 * Uses java.awt.SystemTray which relies on the XEmbed protocol on Linux.
 */
@Log4j2
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = "disable.tray", havingValue = "false", matchIfMissing = true)
@ConditionalOnAwtTray
class TrayServiceAwt {
    private final ApplicationEventPublisher eventPublisher;

    @PostConstruct
    public void init() {
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
                    eventPublisher.publishEvent(new HomePage.ShowMainEvent());
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
