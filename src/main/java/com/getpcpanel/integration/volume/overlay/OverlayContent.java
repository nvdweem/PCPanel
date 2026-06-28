package com.getpcpanel.integration.volume.overlay;

import java.awt.Image;

import javax.annotation.Nullable;

/**
 * What the overlay should show for one trigger: the value (0..1), an optional icon, the
 * controlled-target name, and an optional bar colour sourced from the control's light (a CSS/hex
 * string, or {@code null} to use the configured bar colour — set only when "bar follows light" is on).
 */
public record OverlayContent(float value, @Nullable Image icon, String name, @Nullable String barColorCss) {
    public static OverlayContent of(float value) {
        return new OverlayContent(value, null, "", null);
    }
}
