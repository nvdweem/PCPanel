package com.getpcpanel.overlay;

import java.awt.Image;

import javax.annotation.Nullable;

/**
 * What the overlay should show for one trigger: the value (0..1), an optional icon, the
 * controlled-target name, and the control's current light colour (a CSS/hex string, or {@code null}
 * when the control has no usable light — used only when "bar follows light" is enabled).
 */
public record OverlayContent(float value, @Nullable Image icon, String name, @Nullable String lightColorCss) {
    public static OverlayContent of(float value) {
        return new OverlayContent(value, null, "", null);
    }
}
