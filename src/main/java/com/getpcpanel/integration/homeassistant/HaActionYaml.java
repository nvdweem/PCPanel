package com.getpcpanel.integration.homeassistant;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import lombok.extern.log4j.Log4j2;

/**
 * Parses a Home Assistant "action" YAML block (the format HA's UI produces under Developer Tools →
 * Actions) into the {@code domain.service} + flat data body that the REST API
 * ({@code POST /api/services/{domain}/{service}}) expects.
 *
 * <p>Accepts both the modern {@code action:} key and the legacy {@code service:} key, and folds
 * {@code target:} (entity_id/device_id/area_id/…) and {@code data:} into one body, matching how the
 * REST API takes the entity target inline with the service data.
 *
 * <p>Loaded with {@link SafeConstructor} so only scalars/maps/lists are produced — never arbitrary
 * Java types — which is both safer and friendlier to the native image.
 */
@Log4j2
final class HaActionYaml {
    private HaActionYaml() {
    }

    public record ParsedAction(String domain, String service, Map<String, Object> data) {
    }

    /** @return the parsed action, or {@code null} if the YAML is blank, invalid, or has no action/service. */
    public static ParsedAction parse(String yaml) {
        if (StringUtils.isBlank(yaml)) {
            return null;
        }
        Object root;
        try {
            root = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
        } catch (RuntimeException e) {
            log.warn("Home Assistant action YAML is invalid: {}", e.getMessage());
            return null;
        }
        // An action list (multiple steps) is allowed in HA; we drive a single service call, so take the first.
        if (root instanceof List<?> list && !list.isEmpty()) {
            root = list.get(0);
        }
        if (!(root instanceof Map<?, ?> map)) {
            log.warn("Home Assistant action YAML did not parse to a mapping");
            return null;
        }

        var action = asText(map.get("action"));
        if (action == null) {
            action = asText(map.get("service"));
        }
        if (action == null || !action.contains(".")) {
            log.warn("Home Assistant action YAML has no 'action: domain.service'");
            return null;
        }
        var dot = action.indexOf('.');
        var domain = action.substring(0, dot);
        var service = action.substring(dot + 1);

        var body = new LinkedHashMap<String, Object>();
        if (map.get("target") instanceof Map<?, ?> target) {
            putAll(body, target);
        }
        if (map.get("data") instanceof Map<?, ?> data) {
            putAll(body, data);
        }
        // Tolerate a top-level entity_id (older / hand-written YAML).
        if (map.containsKey("entity_id")) {
            body.put("entity_id", map.get("entity_id"));
        }
        return new ParsedAction(domain, service, body);
    }

    private static void putAll(Map<String, Object> into, Map<?, ?> from) {
        for (var e : from.entrySet()) {
            into.put(String.valueOf(e.getKey()), e.getValue());
        }
    }

    private static String asText(Object o) {
        return o == null ? null : StringUtils.trimToNull(String.valueOf(o));
    }
}
