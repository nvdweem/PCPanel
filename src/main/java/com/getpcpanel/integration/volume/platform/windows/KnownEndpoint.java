package com.getpcpanel.integration.volume.platform.windows;

import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.getpcpanel.integration.volume.platform.DataFlow;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * What an audio endpoint was, kept so the endpoint can be recognised once Windows has replaced it with a new id.
 *
 * <p>Windows names an endpoint "{@code <label> (<hardware>)}": the label is "Speakers" or whatever the user renamed
 * it to, the hardware name comes from the driver ("Schiit Modi 3+", "2- USB Audio Device"). A replaced endpoint
 * loses a rename, so the hardware name is what identifies it; the label only tells endpoints of one piece of
 * hardware apart.
 *
 * @param interfaceName the hardware name as the audio backend reports it; null when it reports none, in which case
 *                      it is read from the name's trailing parentheses
 * @param firstSeen     epoch millis this endpoint was first seen present, 0 when never
 * @param lastSeen      epoch millis this endpoint was last seen present, 0 when never
 */
@RegisterForReflection
public record KnownEndpoint(String name, @Nullable String interfaceName, DataFlow flow, long firstSeen, long lastSeen) {
    /** The number Windows prefixes to the hardware name of the second and later device of a kind ("2- "). */
    private static final Pattern INSTANCE_NUMBER = Pattern.compile("^\\d+-\\s*");

    /** The hardware name without Windows' instance number, or null when there is none. */
    @Nullable
    @JsonIgnore
    public String hardwareName() {
        var hardware = StringUtils.isNotBlank(interfaceName) ? interfaceName : parenthesised(name);
        return hardware == null ? null : StringUtils.trimToNull(INSTANCE_NUMBER.matcher(hardware.trim()).replaceFirst(""));
    }

    /** The name without its hardware part: "Speakers", or the user's rename. */
    @JsonIgnore
    public String label() {
        var hardware = StringUtils.isNotBlank(interfaceName) ? interfaceName : parenthesised(name);
        var suffix = hardware == null ? null : " (" + hardware + ")";
        var result = suffix != null && StringUtils.endsWith(name, suffix) ? name.substring(0, name.length() - suffix.length()) : name;
        return StringUtils.trimToEmpty(result);
    }

    KnownEndpoint seen(long now) {
        return new KnownEndpoint(name, interfaceName, flow, firstSeen == 0 ? now : firstSeen, now);
    }

    /** The content of the parentheses that close {@code name}, nested ones included; null when it ends otherwise. */
    @Nullable
    static String parenthesised(@Nullable String name) {
        if (name == null || !name.endsWith(")")) {
            return null;
        }
        var depth = 0;
        for (var i = name.length() - 1; i >= 0; i--) {
            var c = name.charAt(i);
            if (c == ')') {
                depth++;
            } else if (c == '(' && --depth == 0) {
                return StringUtils.trimToNull(name.substring(i + 1, name.length() - 1));
            }
        }
        return null;
    }
}
