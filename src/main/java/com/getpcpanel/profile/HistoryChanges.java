package com.getpcpanel.profile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

/**
 * Says in a few words what an undo or redo changed ({@code K3 actions · Gaming}, {@code Lighting · Gaming},
 * {@code Settings}), by comparing the saved configuration before and after it. The UI shows it, so a step that
 * changed something on another page is not silent.
 */
final class HistoryChanges {
    private static final int MAX = 4;
    private static final List<String> ACTION_FIELDS = List.of("dialData", "buttonData", "dblButtonData", "releaseButtonData", "holdButtonData");
    /** Not part of what the user edits, or not undone (the active profile). */
    private static final Set<String> IGNORED_DEVICE_FIELDS = Set.of("profiles", "currentProfileName", "capabilities", "providerId", "deviceKindId", "providerConfig");
    private static final Set<String> IGNORED_TOP_FIELDS = Set.of("devices", "templateVersion");

    private HistoryChanges() {
    }

    static List<String> describe(JsonNode before, JsonNode after) {
        var changes = new LinkedHashSet<String>();
        var serials = new LinkedHashSet<String>();
        before.path("devices").fieldNames().forEachRemaining(serials::add);
        after.path("devices").fieldNames().forEachRemaining(serials::add);
        for (var serial : serials) {
            device(before.path("devices").path(serial), after.path("devices").path(serial), changes);
        }
        if (!Objects.equals(withoutFields(before, IGNORED_TOP_FIELDS), withoutFields(after, IGNORED_TOP_FIELDS))) {
            changes.add("Settings");
        }
        var list = new ArrayList<>(changes);
        if (list.size() > MAX) {
            var more = list.size() - (MAX - 1);
            list = new ArrayList<>(list.subList(0, MAX - 1));
            list.add("and " + more + " more");
        }
        return list;
    }

    private static void device(JsonNode before, JsonNode after, Set<String> changes) {
        var name = (after.isMissingNode() ? before : after).path("displayName").asText("a device");
        if (before.isMissingNode() || after.isMissingNode()) {
            changes.add("Device " + name);
            return;
        }
        var labels = after.path("capabilities").path("analogInputs");
        var beforeProfiles = byName(before.path("profiles"));
        var afterProfiles = byName(after.path("profiles"));
        for (var entry : afterProfiles.entrySet()) {
            var old = beforeProfiles.get(entry.getKey());
            if (old != null) {
                profile(entry.getKey(), old, entry.getValue(), labels, changes);
            }
        }
        if (!List.copyOf(beforeProfiles.keySet()).equals(List.copyOf(afterProfiles.keySet()))) {
            changes.add("Profiles of " + name);
        }
        if (!Objects.equals(withoutFields(before, IGNORED_DEVICE_FIELDS), withoutFields(after, IGNORED_DEVICE_FIELDS))) {
            changes.add("Device " + name);
        }
    }

    private static void profile(String profile, JsonNode before, JsonNode after, JsonNode labels, Set<String> changes) {
        var actions = new TreeSet<Integer>();
        for (var field : ACTION_FIELDS) {
            actions.addAll(changedIndexes(before.path(field), after.path(field)));
        }
        actions.forEach(i -> changes.add(label(labels, i) + " actions · " + profile));
        changedIndexes(before.path("knobSettings"), after.path("knobSettings")).forEach(i -> changes.add(label(labels, i) + " settings · " + profile));
        if (!Objects.equals(before.path("lightingConfig"), after.path("lightingConfig"))) {
            changes.add("Lighting · " + profile);
        }
        var handled = new LinkedHashSet<>(ACTION_FIELDS);
        handled.add("knobSettings");
        handled.add("lightingConfig");
        handled.add("name");
        if (!Objects.equals(withoutFields(before, handled), withoutFields(after, handled))) {
            changes.add("Profile " + profile);
        }
    }

    /** Control indexes (the keys of a per-control map) whose value differs; a missing map counts as empty. */
    private static Set<Integer> changedIndexes(JsonNode before, JsonNode after) {
        var keys = new TreeSet<String>();
        before.fieldNames().forEachRemaining(keys::add);
        after.fieldNames().forEachRemaining(keys::add);
        var result = new TreeSet<Integer>();
        for (var key : keys) {
            if (!Objects.equals(before.path(key), after.path(key))) {
                try {
                    result.add(Integer.parseInt(key));
                } catch (NumberFormatException e) {
                    // not a control index; nothing to name it by
                }
            }
        }
        return result;
    }

    private static String label(JsonNode labels, int index) {
        var label = labels.path(index).path("label").asText("");
        return label.isBlank() ? "Control " + (index + 1) : label;
    }

    private static Map<String, JsonNode> byName(JsonNode profiles) {
        var result = new LinkedHashMap<String, JsonNode>();
        profiles.forEach(p -> result.put(p.path("name").asText(""), p));
        return result;
    }

    private static JsonNode withoutFields(JsonNode node, Set<String> fields) {
        if (!node.isObject()) {
            return MissingNode.getInstance();
        }
        var copy = node.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) copy).remove(fields);
        return copy;
    }
}
