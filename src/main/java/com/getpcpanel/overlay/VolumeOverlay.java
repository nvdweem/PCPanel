package com.getpcpanel.overlay;

import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.regex.Pattern;

import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.Timer;

import com.getpcpanel.profile.Save;

public class VolumeOverlay extends JWindow {
    private static final int WIDTH = 340;
    private static final int DEFAULT_HEIGHT = 56;
    private static final int DEFAULT_CORNER_RADIUS = 28;
    private static final int CONTENT_PADDING = 10;
    private static final int ICON_SIZE = 36;
    private static final int DEFAULT_BAR_HEIGHT = 10;
    private static final int DEFAULT_BAR_CORNER_RADIUS = DEFAULT_BAR_HEIGHT;
    private static final int VALUE_LABEL_WIDTH = 36;
    private static final int VALUE_GAP = 8;
    private static final int DISMISS_MS = 2000;   // auto-hide after 2 s
    private static final Pattern RGB_PATTERN = Pattern.compile("rgba?\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern COLOR_COMPONENT_SEPARATOR = Pattern.compile("\\s*,\\s*");

    private static final Color DEFAULT_BG_COLOR = new Color(80, 80, 90, 210);
    private static final Color DEFAULT_BAR_COLOR = new Color(0, 200, 230, 255);
    private static final Color DEFAULT_BAR_TRACK_COLOR = new Color(255, 255, 255, 50);
    private static final Color DEFAULT_TEXT_COLOR = new Color(230, 230, 230, 255);

    private int value;
    private final Timer dismissTimer;
    private Image icon;
    private boolean showNumber = true;
    private int windowCornerRadius = DEFAULT_CORNER_RADIUS;
    private int barHeight = DEFAULT_BAR_HEIGHT;
    private int barCornerRadius = DEFAULT_BAR_CORNER_RADIUS;
    private Color backgroundColor = DEFAULT_BG_COLOR;
    private Color barColor = DEFAULT_BAR_COLOR;
    private Color barTrackColor = DEFAULT_BAR_TRACK_COLOR;
    private Color textColor = DEFAULT_TEXT_COLOR;

    VolumeOverlay() {
        setAlwaysOnTop(true);
        setSize(WIDTH, DEFAULT_HEIGHT);
        setBackground(new Color(0, 0, 0, 0));

        JPanel panel = new OverlayPanel();
        panel.setOpaque(false);
        setContentPane(panel);

        var screen = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((screen.width - WIDTH) / 2, 48);

        dismissTimer = new Timer(DISMISS_MS, _ -> setVisible(false));
        dismissTimer.setRepeats(false);
    }

    public void show(float v, Image icon) {
        this.icon = icon;
        update(Math.round(v * 100f));
    }

    public void setStyles(Save save) {
        showNumber = save.isOverlayShowNumber();
        backgroundColor = parseColor(save.getOverlayBackgroundColor(), DEFAULT_BG_COLOR);
        textColor = parseColor(save.getOverlayTextColor(), DEFAULT_TEXT_COLOR);
        barColor = parseColor(save.getOverlayBarColor(), DEFAULT_BAR_COLOR);
        barTrackColor = parseColor(save.getOverlayBarBackgroundColor(), DEFAULT_BAR_TRACK_COLOR);
        windowCornerRadius = Math.max(0, save.getOverlayWindowCornerRounding());
        barHeight = Math.max(2, save.getOverlayBarHeight());
        barCornerRadius = Math.max(0, save.getOverlayBarCornerRounding());

        var computedHeight = Math.max(DEFAULT_HEIGHT, CONTENT_PADDING * 2 + Math.max(ICON_SIZE, barHeight));
        setSize(WIDTH, computedHeight);
        revalidate();
        repaint();
    }

    private void update(int v) {
        value = Math.clamp(v, 0, 100);
        repaint();
        setVisible(true);
        dismissTimer.restart();
    }

    private class OverlayPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            var g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

            var w = getWidth();
            var h = getHeight();
            var windowArc = Math.min(windowCornerRadius, Math.min(w, h));
            var barArc = Math.min(barCornerRadius, barHeight);

            // ── Background pill ──────────────────────────────────────────
            g2.setColor(backgroundColor);
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, windowArc, windowArc));

            // Subtle top highlight (glass shimmer)
            var gloss = new GradientPaint(
                    0, 0, withAlpha(Color.WHITE, Math.clamp(backgroundColor.getAlpha() / 4, 18, 60)),
                    0, h / 2f, withAlpha(Color.WHITE, 0));
            g2.setPaint(gloss);
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h / 2f, windowArc, windowArc));

            // ── Icon area ────────────────────────────────────────────────
            var iconX = CONTENT_PADDING + 2;
            var iconY = (h - ICON_SIZE) / 2;
            if (icon != null) {
                g2.drawImage(icon, iconX, iconY, ICON_SIZE, ICON_SIZE, null);
            }

            // ── Layout constants ─────────────────────────────────────────
            var afterIcon = iconX + ICON_SIZE + CONTENT_PADDING;
            var valueWidth = showNumber ? VALUE_LABEL_WIDTH : 0;
            var barEndX = w - CONTENT_PADDING - valueWidth - (showNumber ? VALUE_GAP : 0);
            var barY = (h - barHeight) / 2;
            var barWidth = barEndX - afterIcon;

            // ── Progress bar track ───────────────────────────────────────
            g2.setColor(barTrackColor);
            g2.fill(new RoundRectangle2D.Float(afterIcon, barY, barWidth, barHeight, barArc, barArc));

            // ── Progress bar fill ────────────────────────────────────────
            var fillWidth = Math.round(barWidth * (value / 100f));
            if (fillWidth > 0) {
                var fillGrad = new GradientPaint(
                        afterIcon, 0, scaleColor(barColor, 1.15f),
                        afterIcon + fillWidth, 0, scaleColor(barColor, 0.82f));
                g2.setPaint(fillGrad);
                g2.fill(new RoundRectangle2D.Float(afterIcon, barY, fillWidth, barHeight, barArc, barArc));

                // Bright leading cap
                if (fillWidth >= barHeight) {
                    g2.setColor(withAlpha(scaleColor(barColor, 1.35f), Math.clamp(barColor.getAlpha(), 120, 220)));
                    var capX = afterIcon + fillWidth - barHeight;
                    g2.fill(new Ellipse2D.Float(capX, barY, barHeight, barHeight));
                }
            }

            // ── Value label ──────────────────────────────────────────────
            if (showNumber) {
                g2.setColor(textColor);
                g2.setFont(new Font("SF Pro Display", Font.BOLD, 16));
                // Fallback font chain
                if (!g2.getFont().getFamily().equals("SF Pro Display")) {
                    g2.setFont(new Font("Segoe UI", Font.BOLD, 16));
                }
                var label = String.valueOf(value);
                var fm = g2.getFontMetrics();
                var labelX = w - CONTENT_PADDING - valueWidth + (valueWidth - fm.stringWidth(label)) / 2;
                var labelY = (h + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(label, labelX, labelY);
            }

            g2.dispose();
        }
    }

    private static Color parseColor(String value, Color fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        try {
            var trimmed = value.trim();
            if (trimmed.startsWith("#")) {
                return parseHexColor(trimmed);
            }

            var matcher = RGB_PATTERN.matcher(trimmed);
            if (matcher.matches()) {
                var parts = COLOR_COMPONENT_SEPARATOR.split(matcher.group(1));
                if (parts.length == 3 || parts.length == 4) {
                    var red = clampChannel(Integer.parseInt(parts[0]));
                    var green = clampChannel(Integer.parseInt(parts[1]));
                    var blue = clampChannel(Integer.parseInt(parts[2]));
                    var alpha = parts.length == 4 ? parseAlpha(parts[3]) : 255;
                    return new Color(red, green, blue, alpha);
                }
            }
        } catch (RuntimeException ignored) {
            // Fall back to default styling for invalid persisted values.
        }

        return fallback;
    }

    private static Color parseHexColor(String value) {
        var hex = value.substring(1);
        return switch (hex.length()) {
            case 3 -> new Color(
                    Integer.parseInt(hex.substring(0, 1).repeat(2), 16),
                    Integer.parseInt(hex.substring(1, 2).repeat(2), 16),
                    Integer.parseInt(hex.substring(2, 3).repeat(2), 16));
            case 4 -> new Color(
                    Integer.parseInt(hex.substring(0, 1).repeat(2), 16),
                    Integer.parseInt(hex.substring(1, 2).repeat(2), 16),
                    Integer.parseInt(hex.substring(2, 3).repeat(2), 16),
                    Integer.parseInt(hex.substring(3, 4).repeat(2), 16));
            case 6 -> new Color(
                    Integer.parseInt(hex.substring(0, 2), 16),
                    Integer.parseInt(hex.substring(2, 4), 16),
                    Integer.parseInt(hex.substring(4, 6), 16));
            case 8 -> new Color(
                    Integer.parseInt(hex.substring(0, 2), 16),
                    Integer.parseInt(hex.substring(2, 4), 16),
                    Integer.parseInt(hex.substring(4, 6), 16),
                    Integer.parseInt(hex.substring(6, 8), 16));
            default -> throw new IllegalArgumentException("Unsupported color format: " + value);
        };
    }

    private static int parseAlpha(String value) {
        var alpha = Double.parseDouble(value);
        return clampChannel(roundToInt(alpha <= 1 ? alpha * 255 : alpha));
    }

    private static int roundToInt(double value) {
        return Long.valueOf(Math.round(value)).intValue();
    }

    private static int clampChannel(int value) {
        return Math.clamp(value, 0, 255);
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), clampChannel(alpha));
    }

    private static Color scaleColor(Color color, float factor) {
        return new Color(
                clampChannel(Math.round(color.getRed() * factor)),
                clampChannel(Math.round(color.getGreen() * factor)),
                clampChannel(Math.round(color.getBlue() * factor)),
                color.getAlpha());
    }
}
