package com.getpcpanel.template;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.getpcpanel.commands.meta.CommandMeta;
import com.getpcpanel.integration.homeassistant.command.CommandHomeAssistantAction;
import com.getpcpanel.integration.homeassistant.command.CommandHomeAssistantValue;
import com.getpcpanel.integration.mqtt.command.CommandMqttPublish;
import com.getpcpanel.integration.osc.command.CommandOscSend;
import com.getpcpanel.integration.output.command.CommandHttpRequest;
import com.getpcpanel.integration.program.command.CommandRun;
import com.getpcpanel.util.ValueInterpolator;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * Carries a save written before {@code {{ }}} templates over without changing what any field produces. Until then
 * only {@code {{ value }}} rendered, and only in the fields that supported it; every other {@code {{ … }}} was plain
 * text. Each tag the template engine would now render is rewritten into its literal form, and a rewrite is kept only
 * when it renders exactly what the field produced before. A field that became a template later is carried over the
 * same way by the version that made it one. Runs once per version, on the raw save document, recorded by
 * {@link #VERSION_FIELD}.
 */
@Log4j2
@ApplicationScoped
public class TemplateSaveMigration {
    public static final String VERSION_FIELD = "templateVersion";
    public static final int CURRENT_VERSION = 2;
    private static final double SAMPLE_VALUE = 37.5;

    /** Fields that already substituted {@code {{ value }}}. */
    private static final Map<Class<?>, List<String>> VALUE_FIELDS = Map.of(
            CommandHttpRequest.class, List.of("url", "headers", "body"),
            CommandMqttPublish.class, List.of("payload"),
            CommandHomeAssistantValue.class, List.of("action"));
    /** Fields that were plain text. */
    private static final Map<Class<?>, List<String>> TEXT_FIELDS = Map.of(
            CommandMqttPublish.class, List.of("topic"),
            CommandOscSend.class, List.of("address"),
            CommandHomeAssistantAction.class, List.of("action"));
    /** Plain-text fields that became templates in version 2. */
    private static final Map<Class<?>, List<String>> TEXT_FIELDS_V2 = Map.of(
            CommandRun.class, List.of("command"));
    private static final String OVERLAY_NAME = "overlayName";

    @Inject
    TemplateService templates;

    /** A type's fields that became templates in {@code version}. */
    private record Fields(int version, List<String> value, List<String> text) {
    }

    private final Map<String, List<Fields>> fieldsByType = fieldsByType();

    private static Map<String, List<Fields>> fieldsByType() {
        var result = new HashMap<String, List<Fields>>();
        for (var type : Set.of(CommandHttpRequest.class, CommandMqttPublish.class, CommandOscSend.class, CommandHomeAssistantValue.class, CommandHomeAssistantAction.class)) {
            put(result, type, new Fields(1, VALUE_FIELDS.getOrDefault(type, List.of()), TEXT_FIELDS.getOrDefault(type, List.of())));
        }
        TEXT_FIELDS_V2.forEach((type, text) -> put(result, type, new Fields(2, List.of(), text)));
        return result;
    }

    private static void put(Map<String, List<Fields>> result, Class<?> type, Fields fields) {
        result.computeIfAbsent(type.getAnnotation(JsonTypeName.class).value(), k -> new ArrayList<>()).add(fields);
        var meta = type.getAnnotation(CommandMeta.class);
        if (meta != null) {
            for (var legacy : meta.legacyIds()) {
                result.computeIfAbsent(legacy, k -> new ArrayList<>()).add(fields);
            }
        }
    }

    /** Rewrites {@code save} in place; returns whether any field changed. */
    public boolean migrate(JsonNode save) {
        if (!(save instanceof ObjectNode root)) {
            return false;
        }
        var from = root.path(VERSION_FIELD).asInt(0);
        if (from >= CURRENT_VERSION) {
            return false;
        }
        var changed = walk(root, from);
        root.put(VERSION_FIELD, CURRENT_VERSION);
        return changed;
    }

    /** Rewrites the fields that became templates after version {@code from}. */
    private boolean walk(JsonNode node, int from) {
        var changed = false;
        if (node instanceof ObjectNode object) {
            var types = object.has("_type") ? fieldsByType.getOrDefault(object.path("_type").asText(), List.of()) : List.<Fields>of();
            for (var fields : types) {
                if (fields.version() <= from) {
                    continue;
                }
                for (var field : fields.value()) {
                    changed |= rewrite(object, field, true);
                }
                for (var field : fields.text()) {
                    changed |= rewrite(object, field, false);
                }
            }
            if (from < 1 && object.path(OVERLAY_NAME).isTextual()) {
                changed |= rewrite(object, OVERLAY_NAME, false);
            }
            for (var child : object) {
                changed |= walk(child, from);
            }
        } else if (node.isArray()) {
            for (var child : node) {
                changed |= walk(child, from);
            }
        }
        return changed;
    }

    private boolean rewrite(ObjectNode object, String field, boolean valueField) {
        var original = object.path(field).isTextual() ? object.path(field).asText() : null;
        if (!TemplateService.hasTags(original)) {
            return false;
        }
        var migrated = templates.escapeTags(original, inner -> valueField && "value".equals(inner));
        if (migrated.equals(original)) {
            return false;
        }
        var expected = valueField ? ValueInterpolator.interpolate(original, SAMPLE_VALUE) : original;
        var scope = TemplateScope.EMPTY.withValue(TemplateFunctions.normalize(SAMPLE_VALUE));
        var rendered = render(migrated, scope);
        if (!expected.equals(rendered)) {
            log.warn("Kept a saved template as-is: its rewritten form would render differently ({} instead of {})", rendered, expected);
            return false;
        }
        object.put(field, migrated);
        return true;
    }

    @Nullable
    private String render(String template, TemplateScope scope) {
        return templates.render(template, scope, () -> null);
    }
}
