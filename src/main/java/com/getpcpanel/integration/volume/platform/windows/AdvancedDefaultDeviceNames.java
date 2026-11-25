package com.getpcpanel.integration.volume.platform.windows;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.meta.CommandMeta;
import com.getpcpanel.integration.volume.command.CommandVolumeDefaultDeviceAdvanced;
import com.getpcpanel.integration.volume.platform.AudioDevice;
import com.getpcpanel.integration.volume.platform.DataFlow;

import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

/**
 * Pins the device fields of {@link CommandVolumeDefaultDeviceAdvanced} that hold a device name to the endpoint id of the
 * one connected device that name matches, keeping the name as the field's fallback. A field holding a name (an older
 * save, or text typed into the field) otherwise follows whichever device carrying that name Windows lists first. A
 * name that matches several devices (two identical headsets) is left alone: picking one would be a guess. A field
 * holding an id without a name gets its connected device's name.
 */
@Log4j2
final class AdvancedDefaultDeviceNames {
    private record Field(String ref, String name, DataFlow flow) {
    }

    private static final List<Field> FIELDS = List.of(
            new Field("mediaPb", "mediaPbName", DataFlow.dfRender),
            new Field("mediaRec", "mediaRecName", DataFlow.dfCapture),
            new Field("communicationPb", "communicationPbName", DataFlow.dfRender),
            new Field("communicationRec", "communicationRecName", DataFlow.dfCapture));
    private static final Set<String> TYPES = types();

    private AdvancedDefaultDeviceNames() {
    }

    private static Set<String> types() {
        var type = CommandVolumeDefaultDeviceAdvanced.class;
        var result = new HashSet<>(List.of(type.getAnnotation(CommandMeta.class).legacyIds()));
        result.add(type.getAnnotation(JsonTypeName.class).value());
        return Set.copyOf(result);
    }

    /**
     * Rewrites the advanced default device actions anywhere in {@code save} in place.
     *
     * @return whether anything changed
     */
    static boolean pin(JsonNode save, Collection<? extends AudioDevice> present) {
        var changed = false;
        if (save instanceof ObjectNode object) {
            if (TYPES.contains(text(object, Command.TYPE_PROPERTY))) {
                for (var field : FIELDS) {
                    changed |= pin(object, field, present);
                }
            }
            for (var child : object) {
                changed |= pin(child, present);
            }
        } else if (save.isArray()) {
            for (var child : save) {
                changed |= pin(child, present);
            }
        }
        return changed;
    }

    private static boolean pin(ObjectNode command, Field field, Collection<? extends AudioDevice> present) {
        var ref = text(command, field.ref());
        if (StringUtils.isBlank(ref) || StringUtils.isNotBlank(text(command, field.name()))) {
            return false;
        }
        var onFlow = StreamEx.of(present).filter(d -> d.dataflow() == field.flow() && StringUtils.isNotBlank(d.name())).toList();
        if (EndpointReplacements.ENDPOINT_ID.matcher(ref).matches()) {
            var device = StreamEx.of(onFlow).findFirst(d -> ref.equals(d.id()));
            device.ifPresent(d -> command.put(field.name(), d.name()));
            return device.isPresent();
        }
        // The same order of preference as SndCtrlWindows.resolveDeviceRef, but only a single match counts.
        var exact = StreamEx.of(onFlow).filter(d -> StringUtils.equalsIgnoreCase(d.name(), ref)).toList();
        var matches = exact.isEmpty() ? StreamEx.of(onFlow).filter(d -> StringUtils.containsIgnoreCase(d.name(), ref)).toList() : exact;
        if (matches.size() != 1) {
            return false;
        }
        var device = matches.getFirst();
        log.info("Advanced default device '{}' matches only '{}' ({}), keeping that device", ref, device.name(), device.id());
        command.put(field.ref(), device.id());
        command.put(field.name(), ref);
        return true;
    }

    private static String text(ObjectNode object, String field) {
        var value = object.path(field);
        return value.isTextual() ? value.asText() : "";
    }
}
