package com.getpcpanel.integration.volume.overlay;

import java.awt.Image;

import javax.annotation.Nullable;

/**
 * What the overlay should show for one trigger: the value (0..1), an optional icon, the
 * controlled-target name, an optional bar colour sourced from the control's light (a CSS/hex
 * string, or {@code null} to use the configured bar colour — set only when "bar follows light" is on), and the
 * soft-takeover point (0..1) a line marks on the bar, or a negative value for none.
 */
public record OverlayContent(float value, @Nullable Image icon, String name, @Nullable String barColorCss, float marker) {
    public OverlayContent(float value, @Nullable Image icon, String name, @Nullable String barColorCss) {
        this(value, icon, name, barColorCss, -1);
    }

    public static OverlayContent of(float value) {
        return new OverlayContent(value, null, "", null);
    }
}
