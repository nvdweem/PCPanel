package com.getpcpanel.util;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.regex.Pattern;

import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;
import org.apache.commons.io.IOUtils;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import lombok.extern.log4j.Log4j2;

@Log4j2
public abstract class Images {
    private static final Pattern PATH_RE = Pattern.compile("path d=\"(.*?)\"");
    public static final Image lighting = new Image(Objects.requireNonNull(Images.class.getResource("/assets/lighting.png")).toExternalForm());

    private static final SVGPath delete = svg("/assets/icons/close-circle-outline.svg");
    private static final SVGPath chevronDown = svg("/assets/icons/chevron-down.svg");
    private static final SVGPath chevronUp = svg("/assets/icons/chevron-up.svg");
    private static final SVGPath copy = svg("/assets/icons/content-copy.svg");
    private static final SVGPath light = svg("/assets/icons/lightbulb-on-outline.svg");

    public static SVGPath delete() {
        return clone(delete);
    }

    public static SVGPath chevronDown() {
        return clone(chevronDown);
    }

    public static SVGPath chevronUp() {
        return clone(chevronUp);
    }

    public static SVGPath copy() {
        return clone(copy);
    }

    public static SVGPath light() {
        return clone(light);
    }

    private static SVGPath clone(SVGPath content) {
        var result = new SVGPath();
        result.setFill(Color.WHITE);
        result.setContent(content.getContent());
        return result;
    }

    private static SVGPath svg(String filePath) {
        try {
            var result = new SVGPath();
            var pathMatcher = PATH_RE.matcher(IOUtils.toString(Objects.requireNonNull(Images.class.getResourceAsStream(filePath)), Charset.defaultCharset()));
            if (pathMatcher.find()) {
                result.setContent(pathMatcher.group(1));
            }

            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ImageView asImageView(Image image, int size) {
        var imageView = new ImageView(image);
        imageView.setFitWidth(size);
        imageView.setFitHeight(size);
        imageView.setPreserveRatio(true);
        return imageView;
    }

    private static class BufferedImageTranscoder extends ImageTranscoder {
        private BufferedImage img;

        @Override
        public BufferedImage createImage(int width, int height) {
            return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }

        @Override
        public void writeImage(BufferedImage img, TranscoderOutput to) {
            this.img = img;
        }

        public BufferedImage getBufferedImage() {
            return img;
        }
    }

}
