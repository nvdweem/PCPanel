package com.getpcpanel.util.tray.awt;

import java.awt.Desktop;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.desktop.AppReopenedListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Objects;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.SystemUtils;
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
            // On macOS the menu bar is rendered at Retina scale, so scale down from a larger source for a sharper icon
            var iconResource = SystemUtils.IS_OS_MAC ? "/assets/256x256.png" : "/assets/32x32.png";
            var trayIconImage = ImageIO.read(Objects.requireNonNull(TrayServiceAwt.class.getResource(iconResource)));
            var trayIconWidth = new TrayIcon(trayIconImage).getSize().width;
            trayIcon = new TrayIcon(trayIconImage.getScaledInstance(trayIconWidth, -1, 4));
        } catch (UnsupportedOperationException e) {
            log.warn("Tray icon is not supported");
            return;
        } catch (Exception e1) {
            log.error("Unable to initialize tray icon", e1);
            return;
        }
        // On macOS the popup opens on mouse-down and consumes the click, so mouseClicked never fires; a menu item works on all platforms
        var showItem = new MenuItem("Show PCPanel");
        showItem.addActionListener(e -> eventPublisher.publishEvent(new HomePage.ShowMainEvent()));
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
        popup.add(showItem);
        popup.add(exitItem);
        trayIcon.setToolTip("PCPanel");
        trayIcon.setPopupMenu(popup);
        try {
            tray.add(trayIcon);
        } catch (Exception e) {
            log.error("TrayIcon could not be added.", e);
        }
        if (SystemUtils.IS_OS_MAC) {
            registerDockReopenHandler();
        }
    }

    /**
     * On macOS, clicking the Dock icon fires an app-reopened event rather than a tray click.
     */
    private void registerDockReopenHandler() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_EVENT_REOPENED)) {
                Desktop.getDesktop().addAppEventListener((AppReopenedListener) e -> eventPublisher.publishEvent(new HomePage.ShowMainEvent()));
            }
        } catch (Throwable t) {
            log.warn("Unable to register dock reopen handler", t);
        }
    }
}
