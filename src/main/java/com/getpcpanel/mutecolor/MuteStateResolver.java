package com.getpcpanel.mutecolor;

import java.util.Optional;

import com.getpcpanel.commands.Commands;

/**
 * Resolves the current mute state of the thing a control's mute-override colour should track.
 *
 * <p>The mute-colour layer is open for extension through this interface: every integration that can
 * be muted contributes one {@code @ApplicationScoped} resolver, discovered by {@link MuteColorService}
 * via {@code @All List<MuteStateResolver>}. To support a new mutable integration, add a resolver — no
 * change to the orchestrator is needed for the resolution itself.
 */
public interface MuteStateResolver {
    /** Sentinel target meaning "follow whatever this control's own turn command acts on". */
    String FOLLOW = "__follow__";

    /**
     * @param command the control's turn (dial/slider) command list
     * @param target  {@link #FOLLOW} to follow the control's own command, or an integration-specific
     *                name (an audio-device name, a {@code VoiceMeeter: …} pattern) to watch a fixed target
     * @return the current mute state, or empty when this resolver is not responsible for {@code target}
     *         (or cannot currently determine it — e.g. the integration is disconnected)
     */
    Optional<Boolean> resolve(Commands command, String target);
}
