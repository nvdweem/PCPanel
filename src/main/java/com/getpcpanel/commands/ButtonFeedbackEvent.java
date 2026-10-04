package com.getpcpanel.commands;

import java.util.Optional;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.template.TemplateContext;

/**
 * What a button action just did, for the overlay to show: "Spotify · Muted", "Default: Headphones", a profile name.
 * Fired by the action itself ({@link #forCurrentControl} + {@link com.getpcpanel.util.CdiHelper#fire}), synchronously
 * inside the {@link TemplateContext} of the press that ran it.
 *
 * @param serial the device the button is on
 * @param button the button's index in the device's digital input space
 * @param text   what happened, shown as the overlay's name line
 * @param level  a level the action set (0..1), shown as the overlay's bar; null for no bar
 */
public record ButtonFeedbackEvent(String serial, int button, String text, @Nullable Float level) {
    /** Whether the running action runs for a button, so it is worth working out what to report. */
    public static boolean isButtonContext() {
        var scope = TemplateContext.current();
        return scope.serial() != null && scope.button() && scope.control() >= 0;
    }

    /** The event for the button the running action belongs to; empty when it runs for no button or reports nothing. */
    public static Optional<ButtonFeedbackEvent> forCurrentControl(@Nullable String text, @Nullable Float level) {
        if (!isButtonContext() || (StringUtils.isBlank(text) && level == null)) {
            return Optional.empty();
        }
        var scope = TemplateContext.current();
        return Optional.of(new ButtonFeedbackEvent(scope.serial(), scope.control(), StringUtils.defaultString(text).strip(), level));
    }
}
