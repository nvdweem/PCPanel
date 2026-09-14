package com.getpcpanel.template.rest.dto;

import javax.annotation.Nullable;

/**
 * A template to render against a control's current state.
 *
 * @param source  the template text
 * @param serial  the device, or null for no device context
 * @param control the control index
 * @param slot    {@code overlay}, {@code rotate}, {@code press}, {@code dblpress} or {@code release}: which of the
 *                control's actions and values the template belongs to
 * @param min     the command's value mapping, as the command itself would apply it to {@code {{ value }}}
 * @param max     see {@code min}
 * @param formula see {@code min}
 */
public record TemplatePreviewRequestDto(String source, @Nullable String serial, int control, @Nullable String slot,
                                        @Nullable Double min, @Nullable Double max, @Nullable String formula) {
}
