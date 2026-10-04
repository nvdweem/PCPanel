package com.getpcpanel.integration.display;

import java.util.ArrayList;
import java.util.Arrays;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

/** The name a monitor is picked by, the same on every platform: "DELL S3221QS · Display 3 · 3840×2160 · main". */
public final class DisplayNames {
    public static final String SEPARATOR = " · ";

    private DisplayNames() {
    }

    /**
     * @param model  the monitor's model as the system reports it; left out when blank
     * @param number the display's number; left out when 0 or less
     * @param width  the resolution in pixels; left out when 0 or less
     * @param extras further parts, such as "main", appended in order; blank ones are left out
     */
    public static String format(@Nullable String model, int number, int width, int height, String... extras) {
        var parts = new ArrayList<String>();
        if (StringUtils.isNotBlank(model)) {
            parts.add(model.strip());
        }
        if (number > 0) {
            parts.add("Display " + number);
        }
        if (width > 0 && height > 0) {
            parts.add(width + "×" + height);
        }
        Arrays.stream(extras).filter(StringUtils::isNotBlank).forEach(parts::add);
        return parts.isEmpty() ? "Display" : String.join(SEPARATOR, parts);
    }
}
