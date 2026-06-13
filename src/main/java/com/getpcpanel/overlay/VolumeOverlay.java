package com.getpcpanel.overlay;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;

import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

import com.getpcpanel.profile.Save;

/**
 * Swing ({@link JWindow}) implementation of the volume overlay, used on Linux/macOS and on the JVM.
 *
 * <p>On Windows native images the AWT windowing toolkit is unsupported, so
 * {@link Win32VolumeOverlay} is used instead. The actual drawing is delegated to the shared,
 * headless {@link OverlayRenderer}.
 */
public class VolumeOverlay extends JWindow implements OverlayWindow {
    // Install the cross-platform (Metal) Look and Feel before any Swing
    // component is constructed.  This static block runs when VolumeOverlay is
    // first loaded – before the implicit JWindow() super-constructor call –
    // so JPanel / JRootPane can find their ComponentUI delegates.
    static {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
            // If Metal L&F is unavailable in native image, swallow and continue.
        }
    }

    private static final int DISMISS_MS = 2000;   // auto-hide after 2 s

    private final transient OverlayRenderer renderer = new OverlayRenderer();
    private final Timer dismissTimer;

    public VolumeOverlay() {
        setAlwaysOnTop(true);
        setSize(OverlayRenderer.WIDTH, OverlayRenderer.DEFAULT_HEIGHT);
        setBackground(new Color(0, 0, 0, 0));

        JPanel panel = new OverlayPanel();
        panel.setOpaque(false);
        setContentPane(panel);

        var screen = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((screen.width - OverlayRenderer.WIDTH) / 2, 48);

        dismissTimer = new Timer(DISMISS_MS, _ -> setVisible(false));
        dismissTimer.setRepeats(false);
    }

    @Override
    public void show(float v, Image icon) {
        SwingUtilities.invokeLater(() -> {
            renderer.setIcon(icon);
            renderer.setValue(Math.round(v * 100f));
            repaint();
            setVisible(true);
            dismissTimer.restart();
        });
    }

    @Override
    public void setStyles(Save save) {
        SwingUtilities.invokeLater(() -> {
            var height = renderer.setStyles(save);
            setSize(OverlayRenderer.WIDTH, height);
            revalidate();
            repaint();
        });
    }

    @Override
    public Dimension getScreenSize() {
        return Toolkit.getDefaultToolkit().getScreenSize();
    }

    private class OverlayPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            var g2 = (Graphics2D) g.create();
            renderer.render(g2, getWidth(), getHeight());
            g2.dispose();
        }
    }
}
