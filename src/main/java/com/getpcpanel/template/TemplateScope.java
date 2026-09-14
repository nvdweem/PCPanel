package com.getpcpanel.template;

import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.DialValue;

/**
 * What a template is rendered for: the control it belongs to and the values its consumer contributes.
 *
 * @param serial   the device, or null when the render has no device (a preview without one)
 * @param control  the control index in the device's analog ({@code button == false}) or digital input space; -1 when unknown
 * @param button   whether {@code control} is a button rather than a dial/slider
 * @param commands the control's actions, used for the automatic name, the mute state and each integration's {@code target}
 * @param dial     the control's current analog value, or null for a button
 * @param value    the consumer's {@code value} (a command's mapped number, the overlay's percentage)
 * @param name     the consumer's automatic name, resolved only when a template uses it; null to derive it from {@code commands}
 */
public record TemplateScope(
        @Nullable String serial,
        int control,
        boolean button,
        @Nullable Commands commands,
        @Nullable DialValue dial,
        @Nullable Object value,
        @Nullable Supplier<String> name) {
    public static final TemplateScope EMPTY = new TemplateScope(null, -1, false, null, null, null, null);

    public TemplateScope withValue(@Nullable Object value) {
        return new TemplateScope(serial, control, button, commands, dial, value, name);
    }

    public TemplateScope withName(@Nullable Supplier<String> name) {
        return new TemplateScope(serial, control, button, commands, dial, value, name);
    }
}
