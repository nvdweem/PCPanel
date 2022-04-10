package main;

import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.util.Objects;

import javax.imageio.ImageIO;

public class TrayWork {
    public static void tray() {
        PopupMenu popup = new PopupMenu();
        TrayIcon trayIcon;
        SystemTray tray = SystemTray.getSystemTray();
        try {
            BufferedImage trayIconImage = ImageIO.read(Objects.requireNonNull(TrayWork.class.getResource("/assets/32x32.png")));
            int trayIconWidth = new TrayIcon(trayIconImage).getSize().width;
            trayIcon = new TrayIcon(trayIconImage.getScaledInstance(trayIconWidth, -1, 4));
        } catch (Exception e1) {
            e1.printStackTrace();
            return;
        }
        MenuItem exitItem = new MenuItem("Exit");
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
                    Window.reopen();
            }
        });
        popup.add(exitItem);
        trayIcon.setToolTip("PCPanel");
        trayIcon.setPopupMenu(popup);
        try {
            tray.add(trayIcon);
        } catch (Exception e) {
            System.out.println("TrayIcon could not be added.");
        }
    }
}
