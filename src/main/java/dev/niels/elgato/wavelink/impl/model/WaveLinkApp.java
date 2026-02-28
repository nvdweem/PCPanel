package dev.niels.elgato.wavelink.impl.model;

import org.apache.commons.lang3.StringUtils;

public record WaveLinkApp(
        String id,
        String name
) {
    public static final WaveLinkApp EMPTY = new WaveLinkApp("", "");

    public boolean isEmpty() {
        return StringUtils.isBlank(id);
    }
}
