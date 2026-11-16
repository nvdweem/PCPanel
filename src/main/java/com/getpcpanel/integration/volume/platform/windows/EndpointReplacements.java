package com.getpcpanel.integration.volume.platform.windows;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;

/**
 * Decides which audio endpoint ids in the configuration Windows has replaced, and by what.
 *
 * <p>Windows gives an endpoint a new id whenever it builds the device anew: a driver (re)installed by Windows Update
 * or a feature update, the device moved to another USB port. The old endpoint stays behind as not present, and every
 * action keyed to its id stops working. An id is taken over by a present endpoint only when that is unambiguous:
 * <ul>
 *     <li>same direction (playback or recording) and same hardware name; when several share it, the one with the same
 *     label too ("Speakers" vs "Digital Output") — never a guess between equals;</li>
 *     <li>the present endpoint was first seen <i>after</i> the missing one was last seen. Two identical headsets used
 *     side by side are both seen together, so unplugging one never moves its actions onto the other.</li>
 * </ul>
 */
final class EndpointReplacements {
    /** An endpoint id as Windows writes it: {@code {0.0.0.00000000}.{guid}} (render) or {@code {0.0.1.00000000}.{guid}} (capture). */
    static final Pattern ENDPOINT_ID = Pattern.compile("\\{0\\.0\\.[01]\\.00000000}\\.\\{[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}}");

    private EndpointReplacements() {
    }

    /** Every endpoint id {@code text} mentions. */
    static Set<String> referencedIds(String text) {
        return StreamEx.of(ENDPOINT_ID.matcher(text).results()).map(r -> r.group()).toSet();
    }

    /**
     * @param referenced the endpoint ids the configuration mentions
     * @param known      what each endpoint was, present ones included
     * @param present    the ids of the endpoints present now
     * @return missing id → the present id that replaces it
     */
    static Map<String, String> plan(Set<String> referenced, Map<String, KnownEndpoint> known, Set<String> present) {
        var presentEndpoints = EntryStream.of(known).filterKeys(present::contains).toMap();
        var result = new HashMap<String, String>();
        for (var id : referenced) {
            if (present.contains(id)) {
                continue;
            }
            var lost = known.get(id);
            if (lost != null) {
                replacement(lost, presentEndpoints).ifPresent(to -> result.put(id, to));
            }
        }
        return result;
    }

    static Optional<String> replacement(KnownEndpoint lost, Map<String, KnownEndpoint> present) {
        var candidates = EntryStream.of(present)
                .filterValues(p -> p.flow() == lost.flow())
                .filterValues(p -> lost.lastSeen() == 0 || p.firstSeen() > lost.lastSeen())
                .toList();
        var hardware = lost.hardwareName();
        if (hardware == null) {
            return unique(StreamEx.of(candidates).filter(c -> StringUtils.equalsIgnoreCase(c.getValue().name(), lost.name())).toList());
        }
        var sameHardware = StreamEx.of(candidates).filter(c -> StringUtils.equalsIgnoreCase(c.getValue().hardwareName(), hardware)).toList();
        var sameLabel = StreamEx.of(sameHardware).filter(c -> StringUtils.equalsIgnoreCase(c.getValue().label(), lost.label())).toList();
        if (!sameLabel.isEmpty()) {
            return unique(sameLabel);
        }
        return unique(sameHardware);
    }

    private static Optional<String> unique(List<Map.Entry<String, KnownEndpoint>> matches) {
        return matches.size() == 1 ? Optional.of(matches.getFirst().getKey()) : Optional.empty();
    }
}
