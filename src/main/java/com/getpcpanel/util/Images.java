package com.getpcpanel.util;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;

import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import lombok.extern.log4j.Log4j2;

@Log4j2
public abstract class Images {
    private static final Pattern PATH_RE = Pattern.compile("path d=\"(.*?)\"");
    public static final Image lighting = new Image(Objects.requireNonNull(Images.class.getResource("/assets/lighting.png")).toExternalForm());

    private static final SVGPath delete = svg("/assets/icons/close-circle-outline.svg");
    private static final SVGPath magnify = svg("/assets/icons/magnify.svg");
    private static final SVGPath chevronDown = svg("/assets/icons/chevron-down.svg");
    private static final SVGPath chevronUp = svg("/assets/icons/chevron-up.svg");
    private static final SVGPath copy = svg("/assets/icons/content-copy.svg");
    private static final SVGPath light = svg("/assets/icons/lightbulb-on-outline.svg");

    public static SVGPath delete() {
        return clone(delete);
    }

    public static SVGPath magnify() {
        return clone(magnify);
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
}
