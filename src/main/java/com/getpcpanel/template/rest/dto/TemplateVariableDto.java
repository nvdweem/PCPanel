package com.getpcpanel.template.rest.dto;

import javax.annotation.Nullable;

/**
 * One entry of the template editor's completion list.
 *
 * @param name        the entry as the user sees it (a variable, a map key, a function)
 * @param insert      the text completion inserts for it ({@code level}, {@code get('odd-id')}, {@code round(0)})
 * @param kind        {@code value}, {@code object}, {@code map}, {@code list} or {@code function}; everything but
 *                    {@code value} and {@code function} can be drilled into with a {@code .}
 * @param label       a friendly name for an id-keyed entry (the channel name behind a channel id)
 * @param description what the entry means
 * @param value       its current value, when it has one
 */
public record TemplateVariableDto(String name, String insert, String kind, @Nullable String label, @Nullable String description, @Nullable String value) {
}
