package com.getpcpanel.integration.volume.overlay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

import com.getpcpanel.profile.Save;
import com.sun.jna.Platform;

/**
 * The bar fills from its left end to its share of the whole bar; the round knob rides on the end of the fill, solid,
 * standing out above and below the bar when it is the taller of the two.
 */
class OverlayRendererBarTest {
    private static final int BAR_X = 10;
    private static final int BAR_WIDTH = 180;
    private static final int BAR_Y = 10; // the default bar is 10 px high: y 10..19
    private static final int MID = BAR_Y + 5;

    private static BufferedImage bar(int percent) {
        var renderer = new OverlayRenderer();
        renderer.setValue(percent);
        var image = new BufferedImage(200, 30, BufferedImage.TYPE_INT_ARGB);
        var g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        renderer.drawBar(g2, BAR_X, BAR_Y, BAR_WIDTH);
        g2.dispose();
        return image;
    }

    private static int alpha(int argb) {
        return argb >>> 24;
    }

    @Test
    void theThumbIsCentredOnTheEndOfTheFill() {
        var image = bar(50); // fill ends at x = 100
        assertEquals(image.getRGB(97, MID), image.getRGB(103, MID), "the thumb spans both sides of the fill's end");
        assertEquals(0xFF, alpha(image.getRGB(103, MID)), "solid, not the see-through track");
        assertEquals(image.getRGB(98, MID), image.getRGB(100, MID), "one colour across it: the fill does not show through");
    }

    @Test
    void theKnobRidesOnTheEndOfTheFillAtBothEnds() {
        var full = bar(100); // centred on the bar's end, x = 190
        assertEquals(full.getRGB(BAR_X + BAR_WIDTH - 3, MID), full.getRGB(BAR_X + BAR_WIDTH + 3, MID), "half past the end");

        var empty = bar(0); // centred on the bar's start, x = 10
        assertEquals(empty.getRGB(BAR_X - 3, MID), empty.getRGB(BAR_X + 3, MID), "half before the start");
    }

    @Test
    void theThumbStandsOutAboveAndBelowTheBar() {
        var image = bar(50);
        assertEquals(0xFF, alpha(image.getRGB(100, BAR_Y - 1)));
        assertEquals(0xFF, alpha(image.getRGB(100, BAR_Y + 10)));
    }

    @Test
    void withoutAKnobTheFillSpansTheBar() {
        var save = new Save();
        save.setOverlayBarHeight(10);
        save.setOverlayKnobSize(0);
        var renderer = new OverlayRenderer();
        renderer.setStyles(save);
        renderer.setValue(50);
        var image = new BufferedImage(200, 30, BufferedImage.TYPE_INT_ARGB);
        var g2 = image.createGraphics();
        renderer.drawBar(g2, BAR_X, BAR_Y, BAR_WIDTH);
        g2.dispose();
        assertEquals(0, alpha(image.getRGB(100, BAR_Y - 1)), "nothing above the bar");
        assertNotEquals(image.getRGB(BAR_X + 85, MID), image.getRGB(BAR_X + 95, MID), "the fill ends at half the bar");
    }

    /** A bar at {@code percent} with no knob, a green takeover line {@code markerWidth} wide at {@code marker}. */
    private static BufferedImage marked(int percent, int marker, int markerWidth) {
        var save = new Save();
        save.setOverlayBarHeight(10);
        save.setOverlayKnobSize(0);
        save.setOverlayTakeoverMarkerWidth(markerWidth);
        save.setOverlayTakeoverMarkerColor("#00FF00");
        var renderer = new OverlayRenderer();
        renderer.setStyles(save);
        renderer.setValue(percent);
        renderer.setMarker(marker);
        var image = new BufferedImage(200, 30, BufferedImage.TYPE_INT_ARGB);
        var g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        renderer.drawBar(g2, BAR_X, BAR_Y, BAR_WIDTH);
        g2.dispose();
        return image;
    }

    @Test
    void theTakeoverLineMarksItsPointOnTheBar() {
        var image = marked(20, 60, 4); // the line is centred on x = 10 + 180 * 0.6 = 118
        assertEquals(0xFF00FF00, image.getRGB(118, MID), "the line");
        assertEquals(0xFF00FF00, image.getRGB(117, BAR_Y), "across the bar's height");
        assertNotEquals(0xFF00FF00, image.getRGB(125, MID), "only that wide");
        assertNotEquals(0xFF00FF00, image.getRGB(BAR_X + 10, MID), "the fill is not the line");
    }

    /** A green takeover line at 60% (x = 118) with the given look, on a 10 px bar without a knob. */
    private static BufferedImage shaped(int width, int height, int rounding) {
        var save = new Save();
        save.setOverlayBarHeight(10);
        save.setOverlayKnobSize(0);
        save.setOverlayTakeoverMarkerWidth(width);
        save.setOverlayTakeoverMarkerHeight(height);
        save.setOverlayTakeoverMarkerRounding(rounding);
        save.setOverlayTakeoverMarkerColor("#00FF00");
        var renderer = new OverlayRenderer();
        renderer.setStyles(save);
        renderer.setValue(20);
        renderer.setMarker(60);
        var image = new BufferedImage(200, 30, BufferedImage.TYPE_INT_ARGB);
        var g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        renderer.drawBar(g2, BAR_X, BAR_Y, BAR_WIDTH);
        g2.dispose();
        return image;
    }

    @Test
    void theTakeoverLineHasItsOwnHeightCentredOnTheBar() {
        var short_ = shaped(4, 6, 0); // y 12..17
        assertEquals(0xFF00FF00, short_.getRGB(118, MID));
        assertNotEquals(0xFF00FF00, short_.getRGB(118, BAR_Y), "shorter than the bar");

        var tall = shaped(4, 20, 0); // y 5..24
        assertEquals(0xFF00FF00, tall.getRGB(118, BAR_Y - 4), "above the bar");
        assertEquals(0xFF00FF00, tall.getRGB(118, BAR_Y + 13), "below the bar");
    }

    @Test
    void theTakeoverLineCanBeRounded() {
        // A 8x20 line at x 114..121, y 5..24.
        assertEquals(0xFF00FF00, shaped(8, 20, 0).getRGB(114, 5), "square: the corner is filled");
        assertNotEquals(0xFF00FF00, shaped(8, 20, 8).getRGB(114, 5), "rounded: the corner is cut");
        assertEquals(0xFF00FF00, shaped(8, 20, 8).getRGB(117, 15), "the middle stays");
    }

    @Test
    void aTallTakeoverLineMakesRoomInTheWindow() {
        var save = new Save();
        save.setOverlayShowAppName(false);
        save.setOverlayKnobSize(0);
        save.setOverlayTakeoverMarkerHeight(60);
        var withoutTakeover = new OverlayRenderer().setStyles(save);
        save.setSoftTakeoverSliders(true);
        var withTakeover = new OverlayRenderer().setStyles(save);
        assertEquals(true, withTakeover > withoutTakeover, withoutTakeover + " -> " + withTakeover);
    }

    @Test
    void noTakeoverLineAtWidthZeroOrWithoutATakeover() {
        assertNotEquals(0xFF00FF00, marked(20, 60, 0).getRGB(118, MID), "width 0 hides it");
        assertNotEquals(0xFF00FF00, marked(20, -1, 4).getRGB(118, MID), "no takeover, no line");
    }

    /** A whole overlay with a red bar and knob, a 4 px padding and a knob much wider than that. */
    private static BufferedImage overlay(boolean showAppName, boolean showNumber, int percent) {
        var save = new Save();
        save.setOverlayShowAppName(showAppName);
        save.setOverlayShowNumber(showNumber);
        save.setOverlayShowIcon(false);
        save.setOverlayContentPadding(4);
        save.setOverlayKnobSize(24);
        save.setOverlayBarColor("#FF0000");
        save.setOverlayBarBackgroundColor("#FF0000");
        save.setOverlayBackgroundColor("#000000");
        save.setOverlayWindowCornerRounding(0);
        var renderer = new OverlayRenderer();
        var h = renderer.setStyles(save);
        renderer.setValue(percent);
        var image = new BufferedImage(renderer.width(), h, BufferedImage.TYPE_INT_ARGB);
        var g2 = image.createGraphics();
        renderer.render(g2, image.getWidth(), image.getHeight());
        g2.dispose();
        return image;
    }

    /** Whether any pixel within {@code pad} of the image's edges is reddish: the bar or knob (text and gloss are grey). */
    private static boolean redNearTheEdge(BufferedImage image, int pad) {
        for (var y = 0; y < image.getHeight(); y++) {
            for (var x = 0; x < image.getWidth(); x++) {
                var nearEdge = x < pad || y < pad || x >= image.getWidth() - pad || y >= image.getHeight() - pad;
                var rgb = image.getRGB(x, y);
                if (nearEdge && ((rgb >> 16) & 0xFF) > ((rgb >> 8) & 0xFF) + 16) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void theKnobKeepsThePaddingAtBothEnds() {
        // The whole overlay draws text, which on macOS starts AWT's desktop integration when a display is present (the
        // native-image metadata run): its classes then land in the macOS native image, which has no AWT and no overlay.
        assumeFalse(Platform.isMac(), "the overlay is drawn on Windows only");
        for (var showAppName : new boolean[]{true, false}) {
            for (var showNumber : new boolean[]{true, false}) {
                for (var percent : new int[]{0, 100}) {
                    assertEquals(false, redNearTheEdge(overlay(showAppName, showNumber, percent), 4),
                            "name " + showAppName + ", number " + showNumber + ", at " + percent + "%");
                }
            }
        }
    }

    private static BufferedImage styled(int barRounding, int knob, int percent) {
        var save = new Save();
        save.setOverlayBarHeight(10);
        save.setOverlayBarCornerRounding(barRounding);
        save.setOverlayKnobSize(knob);
        var renderer = new OverlayRenderer();
        renderer.setStyles(save);
        renderer.setValue(percent);
        var image = new BufferedImage(200, 30, BufferedImage.TYPE_INT_ARGB);
        var g2 = image.createGraphics();
        renderer.drawBar(g2, BAR_X, BAR_Y, BAR_WIDTH);
        g2.dispose();
        return image;
    }

    /** The track's own colour, from the middle of an empty square bar. */
    private static int track() {
        return styled(0, 0, 0).getRGB(BAR_X + BAR_WIDTH / 2, MID);
    }

    @Test
    void aFullSquareBarIsFilledToItsCorners() {
        var image = styled(0, 14, 100);
        var end = BAR_X + BAR_WIDTH - 1;
        assertNotEquals(track(), image.getRGB(end, BAR_Y), "top-right corner: fill, not track");
        assertNotEquals(track(), image.getRGB(end, BAR_Y + 9), "bottom-right corner");
    }

    @Test
    void noTrackShowsBesideAKnobAsTallAsTheBar() {
        var image = styled(10, 10, 100); // knob = bar height = rounding
        var knobLeft = BAR_X + BAR_WIDTH - 10;
        assertNotEquals(track(), image.getRGB(knobLeft, BAR_Y + 1), "where the knob's circle leaves the corner open: fill");
        assertNotEquals(track(), image.getRGB(knobLeft - 1, BAR_Y), "left of the knob: fill");
    }

    @Test
    void anEmptyBarShowsNoFill() {
        var image = styled(0, 14, 0);
        assertEquals(track(), image.getRGB(BAR_X + 20, BAR_Y), "track right beside the knob");
        assertEquals(track(), image.getRGB(BAR_X + BAR_WIDTH - 1, BAR_Y));
    }

    @Test
    void theFillFollowsTheLevelExactly() {
        var image = styled(0, 0, 50); // no knob in the way: fill ends at x = 100
        assertNotEquals(track(), image.getRGB(98, BAR_Y));
        assertEquals(track(), image.getRGB(102, BAR_Y));
    }
}
