package com.getpcpanel.util.tray.awt;

import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Objects;

import javax.imageio.ImageIO;

import com.getpcpanel.platform.LinuxBuild;
import com.getpcpanel.util.ShowMainEvent;
import com.getpcpanel.util.tray.ITrayService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * AWT-based system tray implementation for X11 Linux, where {@code java.awt.SystemTray} works via the
 * XEmbed protocol. Confined to the Linux build ({@link LinuxBuild}): Windows uses the JNA
 * {@link com.getpcpanel.util.tray.win.TrayServiceWin} (AWT's native toolkit is unreliable in the
 * Windows native image), and Wayland sessions use the StatusNotifierItem D-Bus tray.
 * {@link #init()} is invoked explicitly by {@link com.getpcpanel.util.tray.TrayInitializer}.
 */
@Log4j2
@ApplicationScoped
@LinuxBuild
@AwtTrayImpl
public class TrayServiceAwt implements ITrayService {
    @Inject Event<Object> eventBus;

    @Override
    public void init() {
        try {
            initTray();
        } catch (Throwable t) {
            // AWT relies on the JDK's native awt library. In a GraalVM native image that library
            // (awt.dll + deps) is emitted next to the executable and must be shipped with it; if it
            // is missing, loading it throws UnsatisfiedLinkError (an Error, not an Exception). init()
            // runs from the StartupEvent observer, so an uncaught Throwable would abort boot — a
            // missing tray icon must never do that, so swallow it here.
            log.warn("System tray is unavailable; continuing without a tray icon ({})", t.toString());
        }
    }

    private void initTray() {
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
                    eventBus.fire(new ShowMainEvent());
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
