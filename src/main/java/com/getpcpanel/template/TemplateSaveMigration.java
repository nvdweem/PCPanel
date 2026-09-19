package com.getpcpanel.template;

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
import com.getpcpanel.util.ValueInterpolator;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * Carries a save written before {@code {{ }}} templates over without changing what any field produces. Until then
 * only {@code {{ value }}} rendered, and only in the fields that supported it; every other {@code {{ … }}} was plain
 * text. Each tag the template engine would now render is rewritten into its literal form, and a rewrite is kept only
 * when it renders exactly what the field produced before. Runs once, on the raw save document, recorded by
 * {@link #VERSION_FIELD}.
 */
@Log4j2
@ApplicationScoped
public class TemplateSaveMigration {
    public static final String VERSION_FIELD = "templateVersion";
    public static final int CURRENT_VERSION = 1;
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
    private static final String OVERLAY_NAME = "overlayName";

    @Inject
    TemplateService templates;

    private record Fields(List<String> value, List<String> text) {
    }

    private final Map<String, Fields> fieldsByType = fieldsByType();

    private static Map<String, Fields> fieldsByType() {
        var result = new HashMap<String, Fields>();
        for (var type : Set.of(CommandHttpRequest.class, CommandMqttPublish.class, CommandOscSend.class, CommandHomeAssistantValue.class, CommandHomeAssistantAction.class)) {
            var fields = new Fields(VALUE_FIELDS.getOrDefault(type, List.of()), TEXT_FIELDS.getOrDefault(type, List.of()));
            result.put(type.getAnnotation(JsonTypeName.class).value(), fields);
            var meta = type.getAnnotation(CommandMeta.class);
            if (meta != null) {
                for (var legacy : meta.legacyIds()) {
                    result.put(legacy, fields);
                }
            }
        }
        return result;
    }

    /** Rewrites {@code save} in place; returns whether any field changed. */
    public boolean migrate(JsonNode save) {
        if (!(save instanceof ObjectNode root) || root.path(VERSION_FIELD).asInt(0) >= CURRENT_VERSION) {
            return false;
        }
        var changed = walk(root);
        root.put(VERSION_FIELD, CURRENT_VERSION);
        return changed;
    }

    private boolean walk(JsonNode node) {
        var changed = false;
        if (node instanceof ObjectNode object) {
            var fields = object.has("_type") ? fieldsByType.get(object.path("_type").asText()) : null;
            if (fields != null) {
                for (var field : fields.value()) {
                    changed |= rewrite(object, field, true);
                }
                for (var field : fields.text()) {
                    changed |= rewrite(object, field, false);
                }
            }
            if (object.path(OVERLAY_NAME).isTextual()) {
                changed |= rewrite(object, OVERLAY_NAME, false);
            }
            for (var child : object) {
                changed |= walk(child);
            }
        } else if (node.isArray()) {
            for (var child : node) {
                changed |= walk(child);
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
