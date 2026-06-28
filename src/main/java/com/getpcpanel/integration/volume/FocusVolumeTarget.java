package com.getpcpanel.integration.volume;

import com.getpcpanel.commands.command.Command;

/**
 * One target controlled by a {@link FocusVolumeOverride}. It wraps a volume {@link Command} — the same
 * command model used for control bindings — so a target can be any volume sink the app understands: a
 * process, an audio device, a Wave Link channel, an OBS source, a generic HTTP/OSC/MQTT output, etc.
 *
 * <p>This is a record <em>wrapper</em> around the command rather than a bare {@code Command} so per-target
 * options (an individual curve, a fixed scale, …) can be added later without changing the save format.
 */
public record FocusVolumeTarget(Command command) {
}
