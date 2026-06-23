package com.getpcpanel.overlay;

import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.regex.Pattern;

import com.getpcpanel.profile.Save;

/**
 * Pure, headless rendering of the volume overlay.
 *
 * <p>This class deliberately avoids the AWT/Swing windowing toolkit – it only paints into a
 * supplied {@link Graphics2D}. Headless Java2D (drawing into a {@code BufferedImage}) is fully
 * supported by the GraalVM native image, whereas the Swing windowing toolkit
 * ({@code sun.awt.windows.WToolkit}) is not. The JNA-backed {@link Win32VolumeOverlay} (Windows)
 * uses this renderer to paint into a layered-window bitmap.
 */
class OverlayRenderer {
    static final int WIDTH = 340;
    static final int DEFAULT_HEIGHT = 56;
    private static final int DEFAULT_CORNER_RADIUS = 28;
    private static final int CONTENT_PADDING = 10;
    private static final int ICON_SIZE = 36;
    private static final int DEFAULT_BAR_HEIGHT = 10;
    private static final int DEFAULT_BAR_CORNER_RADIUS = DEFAULT_BAR_HEIGHT;
    private static final int VALUE_GAP = 8;
    private static final Pattern RGB_PATTERN = Pattern.compile("rgba?\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern COLOR_COMPONENT_SEPARATOR = Pattern.compile("\\s*,\\s*");

    private static final Color DEFAULT_BG_COLOR = new Color(80, 80, 90, 210);
    private static final Color DEFAULT_BAR_COLOR = new Color(0, 200, 230, 255);
    private static final Color DEFAULT_BAR_TRACK_COLOR = new Color(255, 255, 255, 50);
    private static final Color DEFAULT_TEXT_COLOR = new Color(230, 230, 230, 255);

    private int value;
    private Image icon;
    private String name = "";
    private Color lightColor;            // parsed control light colour, or null when none/unavailable
    private boolean showNumber = true;
    private boolean twoLine = true;
    private boolean showAppName = true;
    private boolean barFollowsLight;
    private int textSize = Save.DEFAULT_OVERLAY_TEXT_SIZE;
    private int iconSize = ICON_SIZE;
    private int elementGap = CONTENT_PADDING;
    private int windowCornerRadius = DEFAULT_CORNER_RADIUS;
    private int barHeight = DEFAULT_BAR_HEIGHT;
    private int barCornerRadius = DEFAULT_BAR_CORNER_RADIUS;
    private Color backgroundColor = DEFAULT_BG_COLOR;
    private Color barColor = DEFAULT_BAR_COLOR;
    private Color barTrackColor = DEFAULT_BAR_TRACK_COLOR;
    private Color textColor = DEFAULT_TEXT_COLOR;

    void setValue(int value) {
        this.value = Math.clamp(value, 0, 100);
    }

    void setIcon(Image icon) {
        this.icon = icon;
    }

    void setName(String name) {
        this.name = name == null ? "" : name;
    }

    /** The control's current light colour as a CSS/hex string, or {@code null}/blank when there is none. */
    void setLightColor(String css) {
        this.lightColor = css == null || css.isBlank() ? null : parseColor(css, null);
    }

    /**
     * Applies the persisted overlay styling and returns the resulting window height.
     */
    int setStyles(Save save) {
        showNumber = save.isOverlayShowNumber();
        twoLine = save.isOverlayTwoLine();
        showAppName = save.isOverlayShowAppName();
        barFollowsLight = save.isOverlayBarFollowsLight();
        textSize = Math.clamp(save.getOverlayTextSize(), 6, 48);
        iconSize = Math.clamp(save.getOverlayIconSize(), 0, 96);
        elementGap = Math.max(0, save.getOverlayElementGap());
        backgroundColor = parseColor(save.getOverlayBackgroundColor(), DEFAULT_BG_COLOR);
        textColor = parseColor(save.getOverlayTextColor(), DEFAULT_TEXT_COLOR);
        barColor = parseColor(save.getOverlayBarColor(), DEFAULT_BAR_COLOR);
        barTrackColor = parseColor(save.getOverlayBarBackgroundColor(), DEFAULT_BAR_TRACK_COLOR);
        windowCornerRadius = Math.max(0, save.getOverlayWindowCornerRounding());
        barHeight = Math.max(2, save.getOverlayBarHeight());
        barCornerRadius = Math.max(0, save.getOverlayBarCornerRounding());
        return computeHeight();
    }

    int computeHeight() {
        var pad = CONTENT_PADDING;
        if (twoLine) {
            var topRow = Math.max(iconSize, textSize + 4);
            return pad * 2 + topRow + elementGap + barHeight;
        }
        return Math.max(DEFAULT_HEIGHT, pad * 2 + Math.max(Math.max(iconSize, textSize + 4), barHeight));
    }

    /** The bar colour actually used: the control's light when "follow light" is on and one is available. */
    private Color effectiveBarColor() {
        return barFollowsLight && lightColor != null ? lightColor : barColor;
    }

    private String valueLabel() {
        return value + "%";
    }

    private void applyFont(Graphics2D g2, int size) {
        g2.setFont(new Font("Segoe UI", Font.BOLD, size));
    }

    void render(Graphics2D g2, int w, int h) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        var windowArc = Math.min(windowCornerRadius, Math.min(w, h));

        // ── Background pill + glass shimmer ──────────────────────────
        g2.setColor(backgroundColor);
        g2.fill(new RoundRectangle2D.Float(0, 0, w, h, windowArc, windowArc));
        var gloss = new GradientPaint(
                0, 0, withAlpha(Color.WHITE, Math.clamp(backgroundColor.getAlpha() / 4, 18, 60)),
                0, h / 2f, withAlpha(Color.WHITE, 0));
        g2.setPaint(gloss);
        g2.fill(new RoundRectangle2D.Float(0, 0, w, h / 2f, windowArc, windowArc));

        if (twoLine) {
            renderTwoLine(g2, w, h);
        } else {
            renderOneLine(g2, w, h);
        }
    }

    /** [icon] [name] [percent] on the top row, full-width bar beneath. Matches the settings preview. */
    private void renderTwoLine(Graphics2D g2, int w, int h) {
        var pad = CONTENT_PADDING;
        var topRow = Math.max(iconSize, textSize + 4);
        var rowTop = pad;

        var x = pad;
        if (icon != null && iconSize > 0) {
            g2.drawImage(icon, x, rowTop + (topRow - iconSize) / 2, iconSize, iconSize, null);
            x += iconSize + elementGap;
        }

        applyFont(g2, textSize);
        var fm = g2.getFontMetrics();
        var textBaseline = rowTop + (topRow + fm.getAscent() - fm.getDescent()) / 2;

        // Value label sits at the right edge.
        var valueRight = w - pad;
        var valueLeft = valueRight;
        if (showNumber) {
            var label = valueLabel();
            var lw = fm.stringWidth(label);
            valueLeft = valueRight - lw;
            g2.setColor(textColor);
            g2.drawString(label, valueLeft, textBaseline);
        }

        // App name fills the space between the icon and the value, ellipsised if needed.
        if (showAppName && !name.isBlank()) {
            var nameRight = (showNumber ? valueLeft - elementGap : valueRight);
            var avail = nameRight - x;
            if (avail > 4) {
                g2.setColor(textColor);
                g2.drawString(ellipsize(fm, name, avail), x, textBaseline);
            }
        }

        var barY = rowTop + topRow + elementGap;
        drawBar(g2, pad, barY, w - 2 * pad);
    }

    /** [icon] [bar] [percent] on a single row (compact). */
    private void renderOneLine(Graphics2D g2, int w, int h) {
        var pad = CONTENT_PADDING;
        var x = pad;
        if (icon != null && iconSize > 0) {
            g2.drawImage(icon, x, (h - iconSize) / 2, iconSize, iconSize, null);
            x += iconSize + elementGap;
        }

        applyFont(g2, textSize);
        var fm = g2.getFontMetrics();
        var valueWidth = showNumber ? fm.stringWidth("100%") : 0;
        var barEndX = w - pad - (showNumber ? valueWidth + VALUE_GAP : 0);
        var barY = (h - barHeight) / 2;
        drawBar(g2, x, barY, barEndX - x);

        if (showNumber) {
            var label = valueLabel();
            var labelX = w - pad - (valueWidth + fm.stringWidth(label)) / 2;
            var labelY = (h + fm.getAscent() - fm.getDescent()) / 2;
            g2.setColor(textColor);
            g2.drawString(label, labelX, labelY);
        }
    }

    /** Draws the progress-bar track + gradient fill + leading cap at the given position/width. */
    private void drawBar(Graphics2D g2, int x, int y, int barWidth) {
        if (barWidth <= 0) {
            return;
        }
        var barArc = Math.min(barCornerRadius, barHeight);
        var bar = effectiveBarColor();

        g2.setColor(barTrackColor);
        g2.fill(new RoundRectangle2D.Float(x, y, barWidth, barHeight, barArc, barArc));

        var fillWidth = Math.round(barWidth * (value / 100f));
        if (fillWidth > 0) {
            var fillGrad = new GradientPaint(
                    x, 0, scaleColor(bar, 1.15f),
                    x + fillWidth, 0, scaleColor(bar, 0.82f));
            g2.setPaint(fillGrad);
            g2.fill(new RoundRectangle2D.Float(x, y, fillWidth, barHeight, barArc, barArc));

            if (fillWidth >= barHeight) {
                g2.setColor(withAlpha(scaleColor(bar, 1.35f), Math.clamp(bar.getAlpha(), 120, 220)));
                g2.fill(new Ellipse2D.Float(x + fillWidth - barHeight, y, barHeight, barHeight));
            }
        }
    }

    private static String ellipsize(java.awt.FontMetrics fm, String text, int maxWidth) {
        if (fm.stringWidth(text) <= maxWidth) {
            return text;
        }
        var ellipsis = "…";
        var end = text.length();
        while (end > 0 && fm.stringWidth(text.substring(0, end) + ellipsis) > maxWidth) {
            end--;
        }
        return end <= 0 ? ellipsis : text.substring(0, end) + ellipsis;
    }

    static Color parseColor(String value, Color fallback) {
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
