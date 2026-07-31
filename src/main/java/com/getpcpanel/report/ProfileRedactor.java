package com.getpcpanel.report;

import java.util.ArrayList;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.getpcpanel.profile.Save;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Renders the user's configuration as JSON with every credential blanked, so a profile attached to a
 * public bug report cannot carry one. Redaction is by field name over the whole serialised tree rather
 * than by a list of known paths: a credential added to a nested integration record, or moved between
 * records, is covered the moment it is named like one.
 *
 * <p>An unset value is left alone. {@code "obsPassword": ""} and {@code "obsPassword":
 * "&#42;&#42;&#42;redacted&#42;&#42;&#42;"} mean different things to whoever reads the report — the
 * first says the user never configured one, which is often the bug.
 */
@ApplicationScoped
public class ProfileRedactor {
    public static final String REDACTED = "***redacted***";
    /** Field names treated as credentials wherever they appear in the tree. */
    static final Pattern SECRET_FIELD = Pattern.compile("(?i).*(password|secret|token|apikey|api_key|credential).*");
    /**
     * Credentials this app protects without naming them like one. Kept in step with
     * {@link com.getpcpanel.util.SecretMasking}, which makes the MQTT username write-only over the
     * settings API — a report that published it would contradict that rule.
     */
    static final Set<String> SECRET_NAMES = Set.of("username");

    @Inject ObjectMapper mapper;

    public String redactedJson(Save save) {
        var tree = mapper.valueToTree(save);
        redact(tree);
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to render the redacted profile", e);
        }
    }

    static void redact(JsonNode node) {
        if (node instanceof ObjectNode object) {
            // Snapshot the names: the loop replaces values on the node it is walking.
            var names = new ArrayList<String>();
            object.fieldNames().forEachRemaining(names::add);
            for (var name : names) {
                var value = object.get(name);
                if (isSecret(name) && isSet(value)) {
                    object.put(name, REDACTED);
                } else {
                    redact(value);
                }
            }
            return;
        }
        node.forEach(ProfileRedactor::redact);
    }

    static boolean isSecret(String fieldName) {
        return SECRET_FIELD.matcher(fieldName).matches() || SECRET_NAMES.contains(fieldName);
    }

    /** A null or empty value carries nothing to hide, and its absence is itself diagnostic. */
    private static boolean isSet(JsonNode value) {
        return value != null && !value.isNull() && !(value.isTextual() && value.asText().isEmpty());
    }
}
